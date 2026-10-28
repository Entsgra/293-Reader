package com.entsgra.reader.reader.epub

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PathsTest {
	@Test
	fun relativeHrefResolvesAgainstTheNamingFile() {
		assertThat(Paths.resolve("OEBPS/content.opf", "text/ch1.xhtml")).isEqualTo("OEBPS/text/ch1.xhtml")
		assertThat(Paths.resolve("content.opf", "ch1.xhtml")).isEqualTo("ch1.xhtml")
	}

	@Test
	fun dotSegmentsFold() {
		assertThat(Paths.resolve("OEBPS/text/ch1.xhtml", "../images/./a.png")).isEqualTo("OEBPS/images/a.png")
	}

	@Test
	fun climbingAboveTheRootIsRefused() {
		assertThat(Paths.resolve("ch1.xhtml", "../../etc/passwd")).isNull()
	}

	@Test
	fun absolutePathStartsAtTheArchiveRoot() {
		assertThat(Paths.resolve("OEBPS/text/ch1.xhtml", "/OEBPS/a.png")).isEqualTo("OEBPS/a.png")
	}

	@Test
	fun fragmentAndQueryAreDropped() {
		assertThat(Paths.resolve("OEBPS/ch1.xhtml", "ch2.xhtml#note-3")).isEqualTo("OEBPS/ch2.xhtml")
		assertThat(Paths.resolve("OEBPS/ch1.xhtml", "ch2.xhtml?x=1")).isEqualTo("OEBPS/ch2.xhtml")
	}

	@Test
	fun fragmentOnlyPointsAtTheSameFile() {
		assertThat(Paths.resolve("OEBPS/ch1.xhtml", "#note-3")).isEqualTo("OEBPS/ch1.xhtml")
		assertThat(Paths.fragment("#note-3")).isEqualTo("note-3")
		assertThat(Paths.fragment("ch2.xhtml")).isNull()
	}

	@Test
	fun linksOutOfTheBookResolveToNull() {
		assertThat(Paths.resolve("ch1.xhtml", "https://example.com/a.xhtml")).isNull()
		assertThat(Paths.resolve("ch1.xhtml", "mailto:a@example.com")).isNull()
		assertThat(Paths.resolve("ch1.xhtml", "data:image/png;base64,AAAA")).isNull()
	}

	@Test
	fun percentEscapesDecodeAsUtf8AndPlusStays() {
		assertThat(Paths.resolve("OEBPS/a.opf", "Br%C3%BCcke%20one+two.xhtml")).isEqualTo("OEBPS/Brücke one+two.xhtml")
		assertThat(Paths.percentDecode("100%")).isEqualTo("100%")
		assertThat(Paths.percentDecode("%zz")).isEqualTo("%zz")
	}
}
