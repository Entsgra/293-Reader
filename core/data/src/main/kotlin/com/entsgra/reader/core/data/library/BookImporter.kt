package com.entsgra.reader.core.data.library

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.entsgra.reader.core.data.ApplicationScope
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.security.FileCipher
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
	enum class Reason { UNSUPPORTED, RAR, UNREADABLE, NO_SPACE, FAILED }
}

/**
 * Copies files picked through the Storage Access Framework into encrypted app storage. One
 * pass per file: the bytes are hashed while they are encrypted, so a duplicate costs one read
 * and its copy is thrown away. Runs in the application scope, so leaving the screen does not
 * cancel an import.
 */
@Singleton
class BookImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: DatabaseProvider,
	private val files: BookFiles,
	private val cipher: FileCipher,
	private val covers: CoverStore,
	private val formats: BookFormatRegistry,
	@ApplicationScope private val scope: CoroutineScope,
) {
	private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
	val state: StateFlow<ImportState> = _state.asStateFlow()

	/** Imports and the orphan sweep never overlap: the sweep would take a half-written file. */
	private val storage = Mutex()

	/** Starts importing [uris] into [folderId]. Ignored while another import runs. */
	fun import(uris: List<Uri>, folderId: Long?) {
		if (uris.isEmpty() || _state.value is ImportState.Running) return
		_state.value = ImportState.Running(done = 0, total = uris.size)
		scope.launch {
			storage.withLock { withContext(Dispatchers.IO) { importAll(uris, folderId) } }
		}
	}

	/** Clears a finished import's summary. */
	fun dismiss() {
		_state.update { if (it is ImportState.Finished) ImportState.Idle else it }
	}

	/** Removes files a crash left without a database row. Call once at startup. */
	suspend fun sweepOrphans() = storage.withLock {
		val referenced = database.get().bookDao().allFileNames().toSet()
		withContext(Dispatchers.IO) { files.sweep(referenced) }
	}

	private suspend fun importAll(uris: List<Uri>, folderId: Long?) {
		var imported = 0
		var duplicates = 0
		val failures = mutableListOf<ImportFailure>()
		uris.forEachIndexed { index, uri ->
			val name = displayName(uri)
			when (val outcome = importOne(uri, name, folderId)) {
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
			val copy = context.contentResolver.openInputStream(uri)?.use { raw -> copy(raw, name, target) }
				?: return Outcome.Failed(ImportFailure.Reason.UNREADABLE)
			if (copy is Copy.Refused) return Outcome.Failed(copy.reason)
			copy as Copy.Done

			val books = database.get().bookDao()
			if (books.findByHash(copy.hash) != null) {
				target.delete()
				return Outcome.Duplicate
			}
			val id = books.insert(
				BookEntity(
					folderId = folderId,
					title = titleFrom(name),
					author = null,
					format = copy.format,
					fileName = target.name,
					fileSize = copy.size,
					contentHash = copy.hash,
					coverFileName = null,
					pageCount = null,
					addedAt = System.currentTimeMillis(),
					lastOpenedAt = null,
				),
			)
			enrich(id, target, copy)
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
	private fun copy(raw: InputStream, name: String, target: File): Copy {
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
		cipher.encrypt(counted, target)
		return Copy.Done(format, digest.digest().toHex(), counted.count)
	}

	/**
	 * Title, author, page count and cover from the format, when one is registered. Best effort:
	 * a book whose metadata cannot be read is still imported under its file name.
	 */
	private suspend fun enrich(id: Long, file: File, copy: Copy.Done) {
		val format = formats.forId(copy.format) ?: return
		try {
			format.open(EncryptedBookSource(file, copy.size, cipher)).use { doc ->
				val cover = when (doc) {
					is PagedDocument -> doc.cover(CoverStore.COVER_WIDTH, CoverStore.COVER_HEIGHT)
					is FlowDocument -> doc.cover(CoverStore.COVER_WIDTH, CoverStore.COVER_HEIGHT)
				}
				val coverName = cover?.let { bitmap -> covers.save(bitmap).also { bitmap.recycle() } }
				val books = database.get().bookDao()
				val book = books.get(id) ?: return
				books.update(
					book.copy(
						title = doc.metadata.title?.takeIf { it.isNotBlank() }?.trim() ?: book.title,
						author = doc.metadata.author?.takeIf { it.isNotBlank() }?.trim(),
						pageCount = (doc as? PagedDocument)?.pageCount,
						coverFileName = coverName,
					),
				)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			// Keep the book; the reader reports a broken file when it is opened.
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

	private companion object {
		const val BUFFER_BYTES = 64 * 1024
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
