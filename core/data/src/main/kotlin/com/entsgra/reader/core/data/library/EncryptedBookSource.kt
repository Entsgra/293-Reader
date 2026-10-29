package com.entsgra.reader.core.data.library

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.entsgra.reader.core.security.FileCipher
import com.entsgra.reader.reader.api.BookSource
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

/**
 * A [BookSource] over a file encrypted by [FileCipher], under [keyFolderId]'s key (null: the
 * app's). Plaintext stays in memory.
 */
class EncryptedBookSource(
	private val file: File,
	override val size: Long,
	private val cipher: FileCipher,
	private val keyFolderId: Long? = null,
) : BookSource {
	override fun openStream(): InputStream = cipher.openInputStream(file, keyFolderId)

	override fun openChannel(): SeekableByteChannel = cipher.openChannel(file, keyFolderId)

	override fun openMemoryFd(): ParcelFileDescriptor {
		val fd = Os.memfd_create("book", 0)
		val pfd = try {
			ParcelFileDescriptor.dup(fd)
		} finally {
			Os.close(fd)
		}
		try {
			// The stream does not own the descriptor, so it is flushed but not closed.
			val out = FileOutputStream(pfd.fileDescriptor)
			openStream().use { it.copyTo(out, BUFFER_BYTES) }
			out.flush()
			Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_SET)
		} catch (e: Exception) {
			pfd.close()
			throw e
		}
		return pfd
	}

	private companion object {
		const val BUFFER_BYTES = 64 * 1024
	}
}
