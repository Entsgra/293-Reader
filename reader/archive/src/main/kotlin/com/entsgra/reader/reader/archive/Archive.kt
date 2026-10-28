package com.entsgra.reader.reader.archive

import com.entsgra.reader.reader.api.BookOpenException
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException

/**
 * Read-only random access to a zip or 7z archive over a decrypting channel, so entries are
 * read straight from the encrypted book file. Not thread-safe: callers serialise access.
 * Closing the archive closes the channel.
 */
sealed interface Archive : Closeable {
	/** Files in the archive, in stored order. Directories are left out. */
	val entries: List<ArchiveEntry>

	/** The whole of [entry]. Refuses entries over [MAX_ENTRY_BYTES]. */
	fun read(entry: ArchiveEntry): ByteArray

	companion object {
		/** No single page or chapter is anywhere near this; a bigger one is a zip bomb. */
		const val MAX_ENTRY_BYTES = 128L * 1024 * 1024

		/** 7z dictionaries above this would risk running the app out of memory. */
		private const val MAX_7Z_MEMORY_KIB = 192 * 1024

		fun zip(channel: SeekableByteChannel): Archive = opening(channel) {
			ZipArchive(ZipFile.builder().setSeekableByteChannel(channel).get())
		}

		fun sevenZip(channel: SeekableByteChannel): Archive = opening(channel) {
			SevenZipArchive(
				SevenZFile.builder()
					.setSeekableByteChannel(channel)
					.setMaxMemoryLimitKiB(MAX_7Z_MEMORY_KIB)
					.get(),
			)
		}

		private inline fun opening(channel: SeekableByteChannel, open: () -> Archive): Archive =
			try {
				translatingErrors(open)
			} catch (e: Exception) {
				channel.close()
				throw e
			}
	}
}

/** One file inside an [Archive]. [size] is the uncompressed size, -1 when the archive omits it. */
class ArchiveEntry internal constructor(
	val name: String,
	val size: Long,
	internal val handle: Any,
) {
	override fun toString() = name
}

private class ZipArchive(private val zip: ZipFile) : Archive {
	override val entries: List<ArchiveEntry> = zip.entries.asSequence()
		.filterNot { it.isDirectory }
		.map { ArchiveEntry(it.name, it.size, it) }
		.toList()

	override fun read(entry: ArchiveEntry): ByteArray = translatingErrors {
		val zipEntry = entry.handle as ZipArchiveEntry
		if (!zip.canReadEntryData(zipEntry)) {
			val reason = if (zipEntry.generalPurposeBit.usesEncryption()) {
				BookOpenException.Reason.PROTECTED
			} else {
				BookOpenException.Reason.DAMAGED
			}
			throw BookOpenException(reason)
		}
		zip.getInputStream(zipEntry).use { it.readCapped(entry.size) }
	}

	override fun close() = zip.close()
}

private class SevenZipArchive(private val sevenZip: SevenZFile) : Archive {
	override val entries: List<ArchiveEntry> = sevenZip.entries
		.filter { !it.isDirectory && it.hasStream() }
		.map { ArchiveEntry(it.name, it.size, it) }

	/**
	 * Solid archives compress entries as one stream: reading entry n decodes every entry before
	 * it in its block. Reading forward continues where the last read stopped, so a front-to-back
	 * pass costs one decode; going back restarts the block. Callers cache what they read.
	 */
	override fun read(entry: ArchiveEntry): ByteArray = translatingErrors {
		sevenZip.getInputStream(entry.handle as SevenZArchiveEntry).readCapped(entry.size)
	}

	override fun close() = sevenZip.close()
}

private fun InputStream.readCapped(declared: Long): ByteArray {
	if (declared > Archive.MAX_ENTRY_BYTES) throw BookOpenException(BookOpenException.Reason.DAMAGED)
	// The declared size can lie; count what actually comes out.
	val out = ByteArrayOutputStream(if (declared > 0) declared.toInt() else BUFFER_BYTES)
	val buffer = ByteArray(BUFFER_BYTES)
	while (true) {
		val n = read(buffer)
		if (n < 0) break
		if (out.size() + n > Archive.MAX_ENTRY_BYTES) throw BookOpenException(BookOpenException.Reason.DAMAGED)
		out.write(buffer, 0, n)
	}
	return out.toByteArray()
}

private const val BUFFER_BYTES = 64 * 1024

/** Maps archive library failures onto [BookOpenException] so readers see one error type. */
private inline fun <T> translatingErrors(block: () -> T): T = try {
	block()
} catch (e: BookOpenException) {
	throw e
} catch (e: PasswordRequiredException) {
	throw BookOpenException(BookOpenException.Reason.PROTECTED, e)
} catch (e: UnsupportedZipFeatureException) {
	val reason = if (e.feature == UnsupportedZipFeatureException.Feature.ENCRYPTION) {
		BookOpenException.Reason.PROTECTED
	} else {
		BookOpenException.Reason.DAMAGED
	}
	throw BookOpenException(reason, e)
} catch (e: IOException) {
	throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
} catch (e: IllegalArgumentException) {
	// commons-compress reports some malformed headers this way.
	throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
}
