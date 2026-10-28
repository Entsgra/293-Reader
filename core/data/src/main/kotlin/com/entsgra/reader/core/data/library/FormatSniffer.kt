package com.entsgra.reader.core.data.library

import com.entsgra.reader.core.model.BookFormatId

/**
 * Decides a file's format from its first bytes, with the file name as a tie-breaker. Magic
 * bytes win over the extension, so a renamed file is still read correctly.
 */
object FormatSniffer {
	/** How many leading bytes [detect] needs. */
	const val HEADER_BYTES = 1024

	sealed interface Result {
		data class Supported(val format: BookFormatId) : Result

		/** CBR and other RAR archives: refused, see README. */
		data object Rar : Result

		data object Unknown : Result
	}

	fun detect(header: ByteArray, fileName: String): Result {
		val ext = fileName.substringAfterLast('.', "").lowercase()
		return when {
			header.indexOf(PDF) in 0..HEADER_BYTES -> Result.Supported(BookFormatId.PDF)
			header.startsWith(ZIP) -> when {
				header.startsWith(EPUB_MIMETYPE, offset = 30) || ext == "epub" ->
					Result.Supported(BookFormatId.EPUB)
				ext == "cbz" || ext == "zip" -> Result.Supported(BookFormatId.CBZ)
				else -> Result.Unknown
			}
			header.startsWith(SEVEN_ZIP) ->
				if (ext == "cb7" || ext == "7z") Result.Supported(BookFormatId.CB7) else Result.Unknown
			header.startsWith(RAR) -> Result.Rar
			ext == "txt" && looksLikeText(header) -> Result.Supported(BookFormatId.TXT)
			else -> Result.Unknown
		}
	}

	/** No NUL bytes, unless the file opens with a UTF-16 byte order mark. */
	private fun looksLikeText(header: ByteArray): Boolean =
		header.startsWith(UTF16_LE) || header.startsWith(UTF16_BE) || header.none { it == 0.toByte() }

	private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean {
		if (size < offset + prefix.size) return false
		return prefix.indices.all { this[offset + it] == prefix[it] }
	}

	private fun ByteArray.indexOf(needle: ByteArray): Int {
		for (i in 0..size - needle.size) {
			if (startsWith(needle, i)) return i
		}
		return -1
	}

	private val PDF = "%PDF-".toByteArray(Charsets.US_ASCII)
	private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
	/** EPUB OCF: the first entry is an uncompressed `mimetype` file. */
	private val EPUB_MIMETYPE = "mimetypeapplication/epub+zip".toByteArray(Charsets.US_ASCII)
	private val SEVEN_ZIP = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
	private val RAR = "Rar!".toByteArray(Charsets.US_ASCII)
	private val UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
	private val UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}
