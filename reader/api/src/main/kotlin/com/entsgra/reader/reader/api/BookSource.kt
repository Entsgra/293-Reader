package com.entsgra.reader.reader.api

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

/**
 * Plaintext access to a book whose bytes are encrypted at rest. Formats pick the access they
 * need; nothing here writes plaintext to disk.
 */
interface BookSource {
	val size: Long

	/** Sequential stream from the start. */
	fun openStream(): InputStream

	/** Random access, for archives (CBZ, CB7, EPUB). */
	fun openChannel(): SeekableByteChannel

	/**
	 * A seekable file descriptor over an in-memory copy (memfd), for platform APIs such as
	 * PdfRenderer that need one. Costs RAM equal to [size] while open.
	 */
	fun openMemoryFd(): ParcelFileDescriptor
}

/** Something a reader must release: native renderers, decoded caches, file handles. */
interface Releasable : Closeable
