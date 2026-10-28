package com.entsgra.reader.core.data.library

import com.entsgra.reader.core.data.library.FormatSniffer.Result
import com.entsgra.reader.core.model.BookFormatId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatSnifferTest {
	private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
	private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)
	private val zip = bytes(0x50, 0x4B, 0x03, 0x04)

	/** A zip whose first entry is the stored `mimetype` file, as EPUB requires. */
	private val epubHeader = zip + ByteArray(26) + ascii("mimetypeapplication/epub+zip")

	@Test
	fun pdfByMagicWhateverTheName() {
		assertThat(FormatSniffer.detect(ascii("%PDF-1.7\n"), "scan.bin"))
			.isEqualTo(Result.Supported(BookFormatId.PDF))
	}

	@Test
	fun pdfWithJunkBeforeTheHeader() {
		assertThat(FormatSniffer.detect(ByteArray(10) + ascii("%PDF-1.4"), "a.pdf"))
			.isEqualTo(Result.Supported(BookFormatId.PDF))
	}

	@Test
	fun epubByMimetypeEvenWhenRenamed() {
		assertThat(FormatSniffer.detect(epubHeader, "book.zip"))
			.isEqualTo(Result.Supported(BookFormatId.EPUB))
	}

	@Test
	fun zipIsComicOnlyWithAComicName() {
		assertThat(FormatSniffer.detect(zip + ByteArray(40), "issue1.cbz"))
			.isEqualTo(Result.Supported(BookFormatId.CBZ))
		assertThat(FormatSniffer.detect(zip + ByteArray(40), "report.docx")).isEqualTo(Result.Unknown)
	}

	@Test
	fun sevenZip() {
		val header = bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C, 0, 4)
		assertThat(FormatSniffer.detect(header, "vol2.cb7")).isEqualTo(Result.Supported(BookFormatId.CB7))
		assertThat(FormatSniffer.detect(header, "backup.bin")).isEqualTo(Result.Unknown)
	}

	@Test
	fun rarIsRefusedByMagicNotName() {
		assertThat(FormatSniffer.detect(ascii("Rar!\u001A\u0007\u0001\u0000"), "issue1.cbz")).isEqualTo(Result.Rar)
	}

	@Test
	fun textNeedsTxtNameAndNoNulBytes() {
		assertThat(FormatSniffer.detect(ascii("Call me Ishmael."), "moby.txt"))
			.isEqualTo(Result.Supported(BookFormatId.TXT))
		assertThat(FormatSniffer.detect(ascii("Call me Ishmael."), "moby.md")).isEqualTo(Result.Unknown)
		assertThat(FormatSniffer.detect(bytes(0x41, 0, 0x42), "data.txt")).isEqualTo(Result.Unknown)
	}

	@Test
	fun utf16TextKeepsItsNulBytes() {
		assertThat(FormatSniffer.detect(bytes(0xFF, 0xFE, 0x41, 0), "notes.txt"))
			.isEqualTo(Result.Supported(BookFormatId.TXT))
	}

	@Test
	fun emptyFileIsUnknown() {
		assertThat(FormatSniffer.detect(ByteArray(0), "empty.pdf")).isEqualTo(Result.Unknown)
	}
}
