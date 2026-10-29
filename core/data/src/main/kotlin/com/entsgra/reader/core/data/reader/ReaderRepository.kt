package com.entsgra.reader.core.data.reader

import com.entsgra.reader.core.data.ApplicationScope
import com.entsgra.reader.core.data.library.BookFiles
import com.entsgra.reader.core.data.library.EncryptedBookSource
import com.entsgra.reader.core.data.lock.BookLockedException
import com.entsgra.reader.core.data.lock.Sealer
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.entity.ReadingPositionEntity
import com.entsgra.reader.core.database.entity.ReadingSessionEntity
import com.entsgra.reader.core.database.entity.asModel
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.ReadingItem
import com.entsgra.reader.core.model.ReadingPosition
import com.entsgra.reader.core.model.SpreadMode
import com.entsgra.reader.core.security.FileCipher
import com.entsgra.reader.core.security.FolderLockedException
import com.entsgra.reader.core.security.Keyring
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookFormatRegistry
import com.entsgra.reader.reader.api.BookOpenException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opening books and remembering where the reader is. Writes go through the application scope,
 * so a position saved as the reader closes still lands after its screen is gone.
 */
@Singleton
class ReaderRepository @Inject constructor(
	private val database: DatabaseProvider,
	private val files: BookFiles,
	private val cipher: FileCipher,
	private val formats: BookFormatRegistry,
	private val sealer: Sealer,
	private val keyring: Keyring,
	@ApplicationScope private val scope: CoroutineScope,
) {
	/** Keeps writes in call order; the mutex is fair, and each write queues before it suspends. */
	private val writes = Mutex()

	/** The sitting being logged. Only touched while holding [writes]. */
	private var session: OpenSession? = null

	private data class OpenSession(val id: Long, val startedAt: Long)

	/** The book, or null once it is deleted or the folder it is locked in closes. */
	fun book(id: Long): Flow<Book?> = flow {
		emitAll(
			combine(database.get().bookDao().observe(id), keyring.unlocked) { entity, _ ->
				entity?.let(sealer::book)
			},
		)
	}

	/** True while [book]'s locked folder is closed: the reader must let go of it. */
	fun lockedOut(book: Book): Flow<Boolean> =
		keyring.unlocked.map { book.keyFolderId != null && book.keyFolderId !in it }.distinctUntilChanged()

	fun position(bookId: Long): Flow<ReadingPosition?> = flow {
		emitAll(database.get().readingPositionDao().observe(bookId).map { it?.asModel() })
	}

	suspend fun lastPosition(bookId: Long): ReadingPosition? =
		database.get().readingPositionDao().get(bookId)?.asModel()

	fun canRead(book: Book): Boolean = formats.canRead(book.format)

	/**
	 * Opens [book] and marks it as just opened. Throws [BookOpenException] with a reason the
	 * screen can explain. Close the document with [close].
	 */
	suspend fun open(book: Book): BookDocument {
		val format = formats.forId(book.format) ?: throw BookOpenException(BookOpenException.Reason.UNSUPPORTED)
		val document = try {
			format.open(EncryptedBookSource(files.book(book.fileName), book.fileSize, cipher, book.keyFolderId))
		} catch (e: BookOpenException) {
			throw e
		} catch (e: FolderLockedException) {
			throw BookLockedException()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
		}
		try {
			database.get().bookDao().markOpened(book.id, System.currentTimeMillis())
		} catch (e: CancellationException) {
			// The screen went away mid-open; nobody else will close this.
			close(document)
			throw e
		}
		return document
	}

	/** Closes [document] off the main thread, after any render in flight. */
	fun close(document: BookDocument) {
		scope.launch(Dispatchers.IO) { document.close() }
	}

	fun savePosition(bookId: Long, locator: Locator, progress: Float) = write {
		database.get().readingPositionDao().upsert(
			ReadingPositionEntity(bookId, locator, progress.coerceIn(0f, 1f), System.currentTimeMillis()),
		)
	}

	fun setReadingDirection(bookId: Long, direction: ReadingDirection) = write {
		database.get().bookDao().setReadingDirection(bookId, direction)
	}

	fun setSpreadMode(bookId: Long, mode: SpreadMode) = write {
		database.get().bookDao().setSpreadMode(bookId, mode)
	}

	/**
	 * Starts a reading-log entry for [bookId] at 1-based [page] (0 for books without fixed pages)
	 * and [progress] through the book. One sitting is open at a time; [finishSession] closes it.
	 * Both queue behind earlier writes, so they always pair up.
	 */
	fun beginSession(bookId: Long, page: Int, progress: Float) {
		val startedAt = System.currentTimeMillis()
		write {
			val id = database.get().readingSessionDao().insert(
				ReadingSessionEntity.of(
					ReadingItem.Ebook(bookId),
					startedAt = startedAt,
					startPage = page,
					startProgress = progress.coerceIn(0f, 1f),
				),
			)
			session = OpenSession(id, startedAt)
		}
	}

	/** Closes the open sitting. Sittings under [MIN_SESSION_MS] are dropped as noise. */
	fun finishSession(page: Int, progress: Float) {
		val endedAt = System.currentTimeMillis()
		write {
			val open = session ?: return@write
			session = null
			val sessions = database.get().readingSessionDao()
			if (endedAt - open.startedAt < MIN_SESSION_MS) {
				sessions.delete(open.id)
			} else {
				sessions.end(open.id, endedAt, page, progress.coerceIn(0f, 1f))
			}
		}
	}

	private fun write(block: suspend () -> Unit) {
		scope.launch(start = CoroutineStart.UNDISPATCHED) { writes.withLock { block() } }
	}

	private companion object {
		const val MIN_SESSION_MS = 30_000L
	}
}
