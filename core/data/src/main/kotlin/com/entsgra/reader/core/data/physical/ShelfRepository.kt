package com.entsgra.reader.core.data.physical

import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.ReaderDatabase
import com.entsgra.reader.core.database.entity.PhysicalBookEntity
import com.entsgra.reader.core.database.entity.ReadingSessionEntity
import com.entsgra.reader.core.database.entity.asModel
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingSession
import com.entsgra.reader.core.model.ReadingStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** What a physical book needs to be added or edited. Blank author and page count mean unknown. */
data class PhysicalBookDetails(
	val title: String,
	val author: String,
	val pageCount: String,
	val status: ReadingStatus,
)

/** Why [PhysicalBookDetails] or a logged sitting was refused. */
enum class ShelfCheck { OK, BLANK_TITLE, BAD_PAGE_COUNT, BAD_PAGES, BAD_MINUTES }

/**
 * Physical books and the sittings logged against them by hand. Stored in the encrypted
 * database alongside the ebooks, so a shelf is as private as a library.
 */
@Singleton
class ShelfRepository @Inject constructor(
	private val database: DatabaseProvider,
) {
	/** Every physical book, most recently touched first. */
	fun books(): Flow<List<PhysicalBook>> = withDb { db ->
		db.physicalBookDao().observeAll().map { rows -> rows.map(PhysicalBookEntity::asModel) }
	}

	fun book(id: Long): Flow<PhysicalBook?> = withDb { db -> db.physicalBookDao().observe(id).map { it?.asModel() } }

	/** Finished sittings with [bookId], newest first. */
	fun sessions(bookId: Long): Flow<List<ReadingSession>> = withDb { db ->
		db.readingSessionDao().observeForPhysicalBook(bookId).map { rows -> rows.map(ReadingSessionEntity::asModel) }
	}

	/** Adds a book. Returns its id, or the reason the details were refused. */
	suspend fun add(details: PhysicalBookDetails): Result {
		val clean = when (val checked = checkDetails(details)) {
			is Checked.Refused -> return Result.Refused(checked.reason)
			is Checked.Ok -> checked
		}
		val now = System.currentTimeMillis()
		val id = database.get().physicalBookDao().insert(
			PhysicalBookEntity(
				title = clean.title,
				author = clean.author,
				pageCount = clean.pageCount,
				isbn = null,
				status = details.status,
				addedAt = now,
				updatedAt = now,
			),
		)
		return Result.Saved(id)
	}

	suspend fun update(id: Long, details: PhysicalBookDetails): ShelfCheck {
		val clean = when (val checked = checkDetails(details)) {
			is Checked.Refused -> return checked.reason
			is Checked.Ok -> checked
		}
		val dao = database.get().physicalBookDao()
		val book = dao.get(id) ?: return ShelfCheck.OK
		dao.update(
			book.copy(
				title = clean.title,
				author = clean.author,
				pageCount = clean.pageCount,
				status = details.status,
				currentPage = clean.pageCount?.let { book.currentPage.coerceAtMost(it) } ?: book.currentPage,
				updatedAt = System.currentTimeMillis(),
			),
		)
		return ShelfCheck.OK
	}

	suspend fun setStatus(id: Long, status: ReadingStatus) {
		val dao = database.get().physicalBookDao()
		val book = dao.get(id) ?: return
		dao.update(book.copy(status = status, updatedAt = System.currentTimeMillis()))
	}

	/** Deletes the book and, through the foreign key, its sittings. */
	suspend fun delete(id: Long) {
		database.get().physicalBookDao().delete(id)
	}

	/**
	 * Logs a sitting that ended now: [minutes] long, from page [fromPage] to [toPage] (1-based).
	 * Moves the bookmark to [toPage] and the status on as [statusAfterSitting] says.
	 */
	suspend fun logSitting(bookId: Long, fromPage: String, toPage: String, minutes: String): ShelfCheck {
		val db = database.get()
		val book = db.physicalBookDao().get(bookId) ?: return ShelfCheck.OK
		val sitting = checkSitting(fromPage, toPage, minutes, book.pageCount)
		if (sitting !is SittingCheck.Ok) return (sitting as SittingCheck.Refused).reason
		val endedAt = System.currentTimeMillis()
		db.readingSessionDao().insert(
			ReadingSessionEntity(
				bookId = null,
				physicalBookId = bookId,
				startedAt = endedAt - sitting.minutes * MINUTE_MS,
				endedAt = endedAt,
				startPage = sitting.from,
				endPage = sitting.to,
			),
		)
		db.physicalBookDao().update(
			book.copy(
				currentPage = sitting.to,
				status = statusAfterSitting(book.status, sitting.to, book.pageCount),
				updatedAt = endedAt,
			),
		)
		return ShelfCheck.OK
	}

	suspend fun deleteSitting(sessionId: Long) {
		database.get().readingSessionDao().delete(sessionId)
	}

	sealed interface Result {
		data class Saved(val id: Long) : Result
		data class Refused(val reason: ShelfCheck) : Result
	}

	private fun <T> withDb(block: suspend (ReaderDatabase) -> Flow<T>): Flow<T> =
		flow { emitAll(block(database.get())) }

	companion object {
		const val MAX_TITLE = 200
		const val MAX_PAGES = 100_000
		const val MAX_MINUTES = 24 * 60
		private const val MINUTE_MS = 60_000L
	}
}

internal sealed interface Checked {
	data class Ok(val title: String, val author: String?, val pageCount: Int?) : Checked
	data class Refused(val reason: ShelfCheck) : Checked
}

/** Trims the details and parses the page count; blank author and page count become null. */
internal fun checkDetails(details: PhysicalBookDetails): Checked {
	val title = details.title.trim().take(ShelfRepository.MAX_TITLE)
	if (title.isEmpty()) return Checked.Refused(ShelfCheck.BLANK_TITLE)
	val pagesText = details.pageCount.trim()
	val pages = if (pagesText.isEmpty()) {
		null
	} else {
		pagesText.toIntOrNull()?.takeIf { it in 1..ShelfRepository.MAX_PAGES }
			?: return Checked.Refused(ShelfCheck.BAD_PAGE_COUNT)
	}
	return Checked.Ok(title, details.author.trim().ifEmpty { null }, pages)
}

internal sealed interface SittingCheck {
	data class Ok(val from: Int, val to: Int, val minutes: Int) : SittingCheck
	data class Refused(val reason: ShelfCheck) : SittingCheck
}

/** Pages must run forward within the book (when its length is known); minutes up to a day. */
internal fun checkSitting(fromPage: String, toPage: String, minutes: String, pageCount: Int?): SittingCheck {
	val last = pageCount ?: ShelfRepository.MAX_PAGES
	val from = fromPage.trim().toIntOrNull()
	val to = toPage.trim().toIntOrNull()
	if (from == null || to == null || from !in 1..last || to !in from..last) return SittingCheck.Refused(ShelfCheck.BAD_PAGES)
	val mins = minutes.trim().toIntOrNull()?.takeIf { it in 1..ShelfRepository.MAX_MINUTES }
		?: return SittingCheck.Refused(ShelfCheck.BAD_MINUTES)
	return SittingCheck.Ok(from, to, mins)
}

/**
 * A sitting starts a book that was waiting (or set aside), and finishes it on the last page.
 * A finished book stays finished: re-reading a passage does not reopen it.
 */
internal fun statusAfterSitting(status: ReadingStatus, toPage: Int, pageCount: Int?): ReadingStatus = when {
	pageCount != null && toPage >= pageCount -> ReadingStatus.FINISHED
	status == ReadingStatus.FINISHED -> ReadingStatus.FINISHED
	else -> ReadingStatus.READING
}
