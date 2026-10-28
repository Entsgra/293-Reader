package com.entsgra.reader.reader.comic

import android.os.ParcelFileDescriptor
import android.system.Os
import com.entsgra.reader.reader.api.BookSource
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

/** Plaintext [BookSource] over bytes in memory, standing in for an encrypted book file. */
internal class BytesSource(private val bytes: ByteArray) : BookSource {
	override val size: Long = bytes.size.toLong()

	override fun openStream(): InputStream = ByteArrayInputStream(bytes)

	override fun openChannel(): SeekableByteChannel = object : SeekableByteChannel {
		private var position = 0L
		private var open = true

		override fun read(dst: ByteBuffer): Int {
			if (position >= bytes.size) return -1
			val n = minOf(dst.remaining().toLong(), bytes.size - position).toInt()
			dst.put(bytes, position.toInt(), n)
			position += n
			return n
		}

		override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
		override fun position() = position
		override fun position(newPosition: Long) = apply { position = newPosition }
		override fun size() = bytes.size.toLong()
		override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
		override fun isOpen() = open
		override fun close() {
			open = false
		}
	}

	override fun openMemoryFd(): ParcelFileDescriptor {
		val fd = Os.memfd_create("test", 0)
		val pfd = ParcelFileDescriptor.dup(fd).also { Os.close(fd) }
		FileOutputStream(pfd.fileDescriptor).write(bytes)
		Os.lseek(pfd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
		return pfd
	}
}
