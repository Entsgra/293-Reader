package com.entsgra.reader.reader.epub

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowDocument
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.PageSize
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device for Android's own XML parser and BitmapFactory; the parsing rules have JVM tests. */
@RunWith(AndroidJUnit4::class)
class EpubFormatTest {
	private val format = EpubFormat()

	private val opf = """
		<?xml version="1.0" encoding="UTF-8"?>
		<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
		  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
		    <dc:title>Austerlitz</dc:title><dc:creator>W. G. Sebald</dc:creator><dc:language>en</dc:language>
		  </metadata>
		  <manifest>
		    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
		    <item id="c1" href="Text/one.xhtml" media-type="application/xhtml+xml"/>
		    <item id="c2" href="Text/two.xhtml" media-type="application/xhtml+xml"/>
		    <item id="cover" href="Images/cover.png" media-type="image/png" properties="cover-image"/>
		  </manifest>
		  <spine page-progression-direction="rtl"><itemref idref="c1"/><itemref idref="c2"/></spine>
		</package>
	""".trimIndent()

	private val nav = """
		<?xml version="1.0" encoding="UTF-8"?>
		<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
		<nav epub:type="toc"><ol><li><a href="Text/one.xhtml">One</a></li><li><a href="Text/two.xhtml#end">Two</a></li></ol></nav>
		</body></html>
	""".trimIndent()

	private fun chapter(body: String) = """
		<?xml version="1.0" encoding="UTF-8"?>
		<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
		<html xmlns="http://www.w3.org/1999/xhtml"><head><title>x</title></head><body>$body</body></html>
	""".trimIndent()

	private val container = """
		<?xml version="1.0"?>
		<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
		<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
		</container>
	""".trimIndent()

	private fun book(extra: Map<String, ByteArray> = emptyMap()): Map<String, ByteArray> = mapOf(
		"META-INF/container.xml" to container.toByteArray(),
		"OEBPS/content.opf" to opf.toByteArray(),
		"OEBPS/nav.xhtml" to nav.toByteArray(),
		"OEBPS/Text/one.xhtml" to chapter("<h1>One</h1><p>caf&eacute; <a href=\"two.xhtml#end\">on</a></p>").toByteArray(),
		"OEBPS/Text/two.xhtml" to chapter("<p>Two</p><img src=\"../Images/cover.png\" alt=\"Map\"/><p id=\"end\">End</p>").toByteArray(),
		"OEBPS/Images/cover.png" to png(Color.RED),
	) + extra

	@Test
	fun opensWithMetadataContentsAndChapters() = runTest {
		(format.open(BytesSource(epub(book()))) as FlowDocument).use { doc ->
			assertThat(doc.metadata.title).isEqualTo("Austerlitz")
			assertThat(doc.metadata.author).isEqualTo("W. G. Sebald")
			assertThat(doc.metadata.readingDirection).isEqualTo(ReadingDirection.RTL)
			assertThat(doc.language).isEqualTo("en")
			assertThat(doc.chapterCount).isEqualTo(2)
			assertThat(doc.contents.map { it.title to it.target })
				.containsExactly("One" to FlowLink(0), "Two" to FlowLink(1, "end")).inOrder()

			val one = doc.chapter(0)
			assertThat(one.blocks.first()).isInstanceOf(FlowBlock.Heading::class.java)
			val link = (one.blocks[1] as FlowBlock.Paragraph).spans.last()
			assertThat(link.text).isEqualTo("on")
			assertThat(link.link).isEqualTo(FlowLink(1, "end"))

			val two = doc.chapter(1)
			assertThat(two.blocks[1]).isEqualTo(FlowBlock.Image("OEBPS/Images/cover.png", "Map"))
			assertThat(two.anchors["end"]).isEqualTo("Two".length + 1)
			assertThat(doc.imageSize("OEBPS/Images/cover.png")).isEqualTo(PageSize(WIDTH, HEIGHT))
		}
	}

	@Test
	fun coverFitsTheBox() = runTest {
		(format.open(BytesSource(epub(book()))) as FlowDocument).use { doc ->
			val cover = doc.cover(50, 50)!!
			assertThat(cover.width).isAtMost(50)
			assertThat(cover.height).isAtMost(50)
			assertThat(cover.getPixel(cover.width / 2, cover.height / 2)).isEqualTo(Color.RED)
		}
	}

	@Test
	fun drmIsProtected() = runTest {
		val encryption = """
			<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
			<enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/></enc:EncryptedData>
			</encryption>
		""".trimIndent()
		assertThat(reasonFor(epub(book(mapOf("META-INF/encryption.xml" to encryption.toByteArray())))))
			.isEqualTo(BookOpenException.Reason.PROTECTED)
	}

	@Test
	fun missingContainerOrGarbageIsDamaged() = runTest {
		assertThat(reasonFor(epub(book() - "META-INF/container.xml"))).isEqualTo(BookOpenException.Reason.DAMAGED)
		assertThat(reasonFor(ByteArray(4096) { it.toByte() })).isEqualTo(BookOpenException.Reason.DAMAGED)
	}

	@Test
	fun recognisesTheMimetypeWhateverTheName() {
		val header = epub(book()).copyOf(64)
		assertThat(format.canOpen(header, "download.bin")).isTrue()
		assertThat(format.canOpen(byteArrayOf(0x50, 0x4B, 0x03, 0x04), "comic.cbz")).isFalse()
	}

	private suspend fun reasonFor(bytes: ByteArray): BookOpenException.Reason? =
		(runCatching { format.open(BytesSource(bytes)).close() }.exceptionOrNull() as? BookOpenException)?.reason

	private fun png(color: Int): ByteArray {
		val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
		return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
	}

	/** OCF layout: an uncompressed `mimetype` entry first, then everything else. */
	private fun epub(files: Map<String, ByteArray>): ByteArray {
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use { zip ->
			val mimetype = "application/epub+zip".toByteArray()
			zip.putNextEntry(
				ZipEntry("mimetype").apply {
					method = ZipEntry.STORED
					size = mimetype.size.toLong()
					crc = CRC32().apply { update(mimetype) }.value
				},
			)
			zip.write(mimetype)
			zip.closeEntry()
			files.forEach { (name, bytes) ->
				zip.putNextEntry(ZipEntry(name))
				zip.write(bytes)
				zip.closeEntry()
			}
		}
		return out.toByteArray()
	}

	private companion object {
		const val WIDTH = 120
		const val HEIGHT = 180
	}
}
