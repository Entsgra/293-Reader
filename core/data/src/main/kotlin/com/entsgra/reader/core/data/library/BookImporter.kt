package com.entsgra.reader.core.data.library

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.entsgra.reader.core.data.ApplicationScope
import com.entsgra.reader.core.data.lock.BookDetails
import com.entsgra.reader.core.data.lock.Sealer
import com.entsgra.reader.core.data.lock.lockRootOf
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.security.FileCipher
import com.entsgra.reader.core.security.FolderLockedException
import com.entsgra.reader.core.security.Keyring
import com.entsgra.reader.reader.api.BookFormatRegistry
import com.entsgra.reader.reader.api.FlowDocument
import com.entsgra.reader.reader.api.PagedDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ImportState {
	data object Idle : ImportState

	data class Running(val done: Int, val total: Int) : ImportState

	data class Finished(
		val imported: Int,
		val duplicates: Int,
		val failures: List<ImportFailure>,
	) : ImportState
}

data class ImportFailure(val fileName: String, val reason: Reason) {
	enum class Reason { UNSUPPORTED, RAR, UNREADABLE, NO_SPACE, LOCKED, FAILED }
}

/**
 * Copies files picked through the Storage Access Framework into encrypted app storage. One
 * pass per file: the bytes are hashed while they are encrypted, so a duplicate costs one read
 * and its copy is thrown away. Runs in the application scope, so leaving the screen does not
 * cancel an import. Into a locked folder (unlocked at the time), files, covers and details go
 * under the folder's key.
 */
@Singleton
class BookImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: DatabaseProvider,
	private val files: BookFiles,
	private val cipher: FileCipher,
	private val covers: CoverStore,
	private val formats: BookFormatRegistry,
	private val sealer: Sealer,
	private val keyring: Keyring,
	private val storage: StorageLock,
	@ApplicationScope private val scope: CoroutineScope,
) {
	private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
	val state: StateFlow<ImportState> = _state.asStateFlow()

	@Volatile
	private var stopping = false
	private var job: Job? = null

	/** Starts importing [uris] into [folderId]. Ignored while another import runs. */
	fun import(uris: List<Uri>, folderId: Long?) {
		if (uris.isEmpty() || _state.value is ImportState.Running) return
		_state.value = ImportState.Running(done = 0, total = uris.size)
		stopping = false
		job = scope.launch { withContext(Dispatchers.IO) { importAll(uris, folderId) } }
	}

	/** Lets the file being imported finish, skips the rest, and returns once it is done. */
	suspend fun stopAfterCurrent() {
		stopping = true
		job?.join()
	}

	/** Clears a finished import's summary. */
	fun dismiss() {
		_state.update { if (it is ImportState.Finished) ImportState.Idle else it }
	}

	/** Removes files a crash left without a database row. Call once at startup. */
	suspend fun sweepOrphans() = storage.mutex.withLock {
		val referenced = database.get().bookDao().allFileNames().toSet()
		withContext(Dispatchers.IO) { files.sweep(referenced) }
	}

	private suspend fun importAll(uris: List<Uri>, folderId: Long?) {
		var imported = 0
		var duplicates = 0
		val failures = mutableListOf<ImportFailure>()
		for ((index, uri) in uris.withIndex()) {
			if (stopping) break
			val name = displayName(uri)
			// Per file, so the sweep or a re-encryption waits for one book, not the whole batch.
			when (val outcome = storage.mutex.withLock { importOne(uri, name, folderId) }) {
				Outcome.Imported -> imported++
				Outcome.Duplicate -> duplicates++
				is Outcome.Failed -> failures += ImportFailure(name, outcome.reason)
			}
			_state.value = ImportState.Running(done = index + 1, total = uris.size)
		}
		_state.value = ImportState.Finished(imported, duplicates, failures)
	}

	private suspend fun importOne(uri: Uri, name: String, folderId: Long?): Outcome {
		val target = files.newBookFile()
		try {
			val db = database.get()
			val key = lockRootOf(folderId, db.folderDao().all().associateBy { it.id })
			if (key != null && keyring[key] == null) return Outcome.Failed(ImportFailure.Reason.LOCKED)
			val copy = context.contentResolver.openInputStream(uri)?.use { raw -> copy(raw, name, target, key) }
				?: return Outcome.Failed(ImportFailure.Reason.UNREADABLE)
			if (copy is Copy.Refused) return Outcome.Failed(copy.reason)
			copy as Copy.Done

			val books = db.bookDao()
			// Only books under the app key can be compared: sealed hashes are not readable here.
			if (books.findByHash(copy.hash) != null) {
				target.delete()
				return Outcome.Duplicate
			}
			val stored = sealer.store(BookDetails(titleFrom(name), null, copy.hash), target.name, key)
			val id = books.insert(
				BookEntity(
					folderId = folderId,
					title = stored.title,
					author = stored.author,
					format = copy.format,
					fileName = target.name,
					fileSize = copy.size,
					contentHash = stored.contentHash,
					coverFileName = null,
					pageCount = null,
					addedAt = System.currentTimeMillis(),
					lastOpenedAt = null,
					keyFolderId = key,
					sealed = stored.sealed,
				),
			)
			enrich(id, target, copy.size, copy.format, keepDetails = false)
			return Outcome.Imported
		} catch (e: CancellationException) {
			target.delete()
			throw e
		} catch (e: Exception) {
			target.delete()
			return Outcome.Failed(reasonFor(e))
		}
	}

	/** Sniffs the header, then streams the file through SHA-256 into its encrypted copy. */
	private fun copy(raw: InputStream, name: String, target: File, keyFolderId: Long?): Copy {
		val input = BufferedInputStream(raw, BUFFER_BYTES)
		input.mark(FormatSniffer.HEADER_BYTES)
		val header = input.readUpTo(FormatSniffer.HEADER_BYTES)
		input.reset()
		val format = when (val result = FormatSniffer.detect(header, name)) {
			is FormatSniffer.Result.Supported -> result.format
			FormatSniffer.Result.Rar -> return Copy.Refused(ImportFailure.Reason.RAR)
			FormatSniffer.Result.Unknown -> return Copy.Refused(ImportFailure.Reason.UNSUPPORTED)
		}
		val digest = MessageDigest.getInstance("SHA-256")
		val counted = CountingInputStream(DigestInputStream(input, digest))
		cipher.encrypt(counted, target, keyFolderId)
		return Copy.Done(format, digest.digest().toHex(), counted.count)
	}

	/**
	 * Fills in metadata for books imported before their format had a reader, or before the
	 * current [METADATA_VERSION]. Keeps titles and authors the person may have edited. Call once
	 * at startup, after [sweepOrphans].
	 */
	suspend fun backfillMetadata() {
		val books = database.get().bookDao()
		val pending = books.withMetadataBefore(METADATA_VERSION, formats.ids.map { it.name })
		for (book in pending) {
			// A locked folder's books wait until it is open at startup; they keep their old details.
			val key = book.keyFolderId
			if (key != null && keyring[key] == null) continue
			// Per book, so an import started meanwhile waits for one book, not the whole pass.
			storage.mutex.withLock {
				withContext(Dispatchers.IO) {
					enrich(book.id, files.book(book.fileName), book.fileSize, book.format, keepDetails = true)
				}
			}
		}
	}

	/**
	 * Title, author, page count, reading direction and cover from the format, when one is
	 * registered. Best effort: a book whose metadata cannot be read keeps its file name as title,
	 * and is not retried until [METADATA_VERSION] changes. With [keepDetails], an existing title
	 * stays and an existing author is only filled when empty.
	 */
	private suspend fun enrich(id: Long, file: File, size: Long, format: BookFormatId, keepDetails: Boolean) {
		val reader = formats.forId(format) ?: return
		val books = database.get().bookDao()
		val key = books.get(id)?.keyFolderId
		try {
			reader.open(EncryptedBookSource(file, size, cipher, key)).use { doc ->
				val book = books.get(id) ?: return
				val current = sealer.details(book) ?: return
				val cover = when (doc) {
					is PagedDocument -> doc.cover(CoverStore.COVER_WIDTH, CoverStore.COVER_HEIGHT)
					is FlowDocument -> doc.cover(CoverStore.COVER_WIDTH, CoverStore.COVER_HEIGHT)
				}
				val coverName = cover?.let { bitmap -> covers.save(bitmap, book.keyFolderId).also { bitmap.recycle() } }
				val title = doc.metadata.title?.trim()?.takeIf { it.isNotEmpty() }
				val author = doc.metadata.author?.trim()?.takeIf { it.isNotEmpty() }
				val details = BookDetails(
					title = if (keepDetails) current.title else title ?: current.title,
					author = if (keepDetails) current.author ?: author else author,
					contentHash = current.contentHash,
				)
				val pageCount = (doc as? PagedDocument)?.pageCount ?: book.pageCount
				val direction = doc.metadata.readingDirection ?: book.readingDirection
				if (book.keyFolderId == null) {
					books.applyMetadata(
						id = id,
						title = details.title,
						author = details.author,
						pageCount = pageCount,
						coverFileName = coverName ?: book.coverFileName,
						direction = direction,
						version = METADATA_VERSION,
					)
				} else {
					val stored = sealer.store(details, book.fileName, book.keyFolderId)
					books.writeDetails(id, stored.title, stored.author, stored.contentHash, stored.sealed)
					books.applyMetadataSealed(id, pageCount, coverName ?: book.coverFileName, direction, METADATA_VERSION)
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			// Keep the book; the reader reports a broken file when it is opened.
			books.setMetadataVersion(id, METADATA_VERSION)
		}
	}

	private fun displayName(uri: Uri): String {
		val queried = runCatching {
			context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
				?.use { if (it.moveToFirst()) it.getString(0) else null }
		}.getOrNull()
		return queried ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
	}

	private fun reasonFor(e: Exception): ImportFailure.Reason {
		val errno = generateSequence<Throwable>(e) { it.cause }
			.filterIsInstance<ErrnoException>()
			.firstOrNull()?.errno
		return when {
			e is FolderLockedException -> ImportFailure.Reason.LOCKED
			errno == OsConstants.ENOSPC -> ImportFailure.Reason.NO_SPACE
			e is FileNotFoundException || e is SecurityException -> ImportFailure.Reason.UNREADABLE
			e is SQLiteConstraintException -> ImportFailure.Reason.FAILED
			e is IOException && e.message?.contains("ENOSPC") == true -> ImportFailure.Reason.NO_SPACE
			e is IOException -> ImportFailure.Reason.UNREADABLE
			else -> ImportFailure.Reason.FAILED
		}
	}

	private sealed interface Copy {
		data class Done(val format: BookFormatId, val hash: String, val size: Long) : Copy
		data class Refused(val reason: ImportFailure.Reason) : Copy
	}

	private sealed interface Outcome {
		data object Imported : Outcome
		data object Duplicate : Outcome
		data class Failed(val reason: ImportFailure.Reason) : Outcome
	}

	companion object {
		/** Bump when formats learn to extract more, so existing books are read again. */
		const val METADATA_VERSION = 1
		private const val BUFFER_BYTES = 64 * 1024
	}
}

/** "the_rings_of_saturn.epub" to "the rings of saturn". */
internal fun titleFrom(fileName: String): String {
	val base = fileName.substringBeforeLast('.').ifBlank { fileName }
	return base.replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifEmpty { fileName }
}

private fun InputStream.readUpTo(max: Int): ByteArray {
	val buffer = ByteArray(max)
	var read = 0
	while (read < max) {
		val n = read(buffer, read, max - read)
		if (n < 0) break
		read += n
	}
	return buffer.copyOf(read)
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
	var count = 0L
		private set

	override fun read(): Int = super.read().also { if (it >= 0) count++ }

	override fun read(b: ByteArray, off: Int, len: Int): Int =
		super.read(b, off, len).also { if (it > 0) count += it }

	override fun skip(n: Long): Long = super.skip(n).also { count += it }
}
