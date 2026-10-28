package com.entsgra.reader.reader.epub

import com.entsgra.reader.reader.api.BookOpenException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubPackageTest {
	init {
		useKxml()
	}

	@Test
	fun containerNamesThePackageFile() {
		val container = """
			<?xml version="1.0"?>
			<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
			  <rootfiles>
			    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
			  </rootfiles>
			</container>
		""".trimIndent()
		assertThat(EpubPackage.rootfile(container.toByteArray())).isEqualTo("OEBPS/content.opf")
	}

	@Test
	fun epub3PackageGivesMetadataSpineCoverAndNav() {
		val opf = """
			<?xml version="1.0" encoding="UTF-8"?>
			<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
			  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
			    <dc:title>  Die   Ringe des Saturn </dc:title>
			    <dc:creator>W. G. Sebald</dc:creator>
			    <dc:language>de</dc:language>
			  </metadata>
			  <manifest>
			    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
			    <item id="c1" href="text/one.xhtml" media-type="application/xhtml+xml"/>
			    <item id="c2" href="text/two.xhtml" media-type="application/xhtml+xml"/>
			    <item id="css" href="style.css" media-type="text/css"/>
			    <item id="img" href="images/front.jpg" media-type="image/jpeg" properties="cover-image"/>
			  </manifest>
			  <spine page-progression-direction="rtl">
			    <itemref idref="c1"/>
			    <itemref idref="css"/>
			    <itemref idref="missing"/>
			    <itemref idref="c2"/>
			  </spine>
			</package>
		""".trimIndent()
		val pkg = EpubPackage.parse("OEBPS/content.opf", opf.toByteArray())
		assertThat(pkg.title).isEqualTo("Die Ringe des Saturn")
		assertThat(pkg.author).isEqualTo("W. G. Sebald")
		assertThat(pkg.language).isEqualTo("de")
		assertThat(pkg.rightToLeft).isTrue()
		assertThat(pkg.spine.map { it.path }).containsExactly("OEBPS/text/one.xhtml", "OEBPS/text/two.xhtml").inOrder()
		assertThat(pkg.coverPath).isEqualTo("OEBPS/images/front.jpg")
		assertThat(pkg.navPath).isEqualTo("OEBPS/nav.xhtml")
	}

	@Test
	fun epub2PackageFindsCoverByMetaAndNcxBySpine() {
		val opf = """
			<?xml version="1.0"?>
			<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
			  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
			    <dc:title>Emma</dc:title>
			    <dc:creator opf:role="aut">Jane Austen</dc:creator>
			    <dc:creator opf:role="ill">Hugh Thomson</dc:creator>
			    <meta name="cover" content="cov"/>
			  </metadata>
			  <manifest>
			    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
			    <item id="cov" href="cover.png" media-type="image/png"/>
			    <item id="c1" href="ch1.html" media-type="application/xhtml+xml"/>
			  </manifest>
			  <spine toc="ncx"><itemref idref="c1"/></spine>
			</package>
		""".trimIndent()
		val pkg = EpubPackage.parse("content.opf", opf.toByteArray())
		assertThat(pkg.author).isEqualTo("Jane Austen")
		assertThat(pkg.coverPath).isEqualTo("cover.png")
		assertThat(pkg.ncxPath).isEqualTo("toc.ncx")
		assertThat(pkg.navPath).isNull()
		assertThat(pkg.rightToLeft).isFalse()
	}

	@Test
	fun spineWithNothingReadableIsEmpty() {
		val opf = """
			<package><manifest><item id="a" href="a.css" media-type="text/css"/></manifest>
			<spine><itemref idref="a"/></spine></package>
		""".trimIndent()
		val e = assertThrows(BookOpenException::class.java) { EpubPackage.parse("content.opf", opf.toByteArray()) }
		assertThat(e.reason).isEqualTo(BookOpenException.Reason.EMPTY)
	}

	@Test
	fun fontObfuscationIsNotDrm() {
		val fonts = """
			<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
			  <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/></enc:EncryptedData>
			</encryption>
		""".trimIndent()
		val drm = fonts.replace("http://www.idpf.org/2008/embedding", "http://www.w3.org/2001/04/xmlenc#aes128-cbc")
		assertThat(EpubPackage.isProtected(fonts.toByteArray())).isFalse()
		assertThat(EpubPackage.isProtected(drm.toByteArray())).isTrue()
	}

	@Test
	fun navTocKeepsDepthAndSkipsOtherNavs() {
		val nav = """
			<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
			  <nav epub:type="landmarks"><ol><li><a href="text/one.xhtml">Start</a></li></ol></nav>
			  <nav epub:type="toc"><ol>
			    <li><a href="text/one.xhtml">One</a>
			      <ol><li><a href="text/one.xhtml#s2"><span>One,</span> part two</a></li></ol>
			    </li>
			    <li><a href="text/two.xhtml">Two</a></li>
			  </ol></nav>
			</body></html>
		""".trimIndent()
		val entries = EpubToc.fromNav("OEBPS/nav.xhtml", nav.toByteArray())
		assertThat(entries).containsExactly(
			RawTocEntry("One", "OEBPS/text/one.xhtml", null, 0),
			RawTocEntry("One, part two", "OEBPS/text/one.xhtml", "s2", 1),
			RawTocEntry("Two", "OEBPS/text/two.xhtml", null, 0),
		).inOrder()
	}

	@Test
	fun ncxTocNestsNavPoints() {
		val ncx = """
			<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
			  <navPoint id="a"><navLabel><text>Volume I</text></navLabel><content src="ch1.html"/>
			    <navPoint id="b"><navLabel><text>Chapter 1</text></navLabel><content src="ch1.html#c1"/></navPoint>
			  </navPoint>
			</navMap></ncx>
		""".trimIndent()
		assertThat(EpubToc.fromNcx("toc.ncx", ncx.toByteArray())).containsExactly(
			RawTocEntry("Volume I", "ch1.html", null, 0),
			RawTocEntry("Chapter 1", "ch1.html", "c1", 1),
		).inOrder()
	}
}
