package com.entsgra.reader.core.security

import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * Writes [file] through [block] and fsyncs it before returning. The stream handed to [block]
 * ignores close(), so wrapping streams (Tink's close their sink) cannot close the fd before sync.
 */
internal fun writeDurably(file: File, block: (OutputStream) -> Unit) {
	FileOutputStream(file).use { raw ->
		block(object : FilterOutputStream(raw) {
			override fun write(b: ByteArray, off: Int, len: Int) = raw.write(b, off, len)
			override fun close() = flush()
		})
		raw.flush()
		raw.fd.sync()
	}
}
