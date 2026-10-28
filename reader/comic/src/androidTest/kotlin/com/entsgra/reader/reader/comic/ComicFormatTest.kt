package com.entsgra.reader.reader.comic

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookFormat
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.PagedDocument
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicFormatTest {
	private val pool = BitmapPool(maxBytes = 32L * 1024 * 1024)
	private val cbz = CbzFormat(pool)
	private val cb7 = Cb7Format(pool)

	private val pages = listOf(
		"page10.png" to Color.BLUE,
		"page2.png" to Color.GREEN,
		"page1.png" to Color.RED,
	)

	@Test
	fun cbzPagesComeInNaturalOrder() = runTest {
		open(cbz, zip(pages + extras())).use { doc ->
			assertThat(doc.pageCount).isEqualTo(3)
			assertThat(centre(doc, 0)).isEqualTo(Color.RED)
			assertThat(centre(doc, 1)).isEqualTo(Color.GREEN)
			assertThat(centre(doc, 2)).isEqualTo(Color.BLUE)
		}
	}

	@Test
	fun pageSizeComesFromTheImage() = runTest {
		open(cbz, zip(pages)).use { doc ->
			assertThat(doc.pageSize(0)).isEqualTo(PageSize(WIDTH, HEIGHT))
		}
	}

	@Test
	fun comicInfoGivesTitleWriterAndMangaDirection() = runTest {
		val info = """
			<?xml version="1.0"?>
			<ComicInfo><Series>Vertigo</Series><Number>3</Number><Writer>W. G. Sebald</Writer><Manga>YesAndRightToLeft</Manga></ComicInfo>
		""".trimIndent()
		open(cbz, zip(pages, mapOf("ComicInfo.xml" to info.toByteArray()))).use { doc ->
			assertThat(doc.metadata.title).isEqualTo("Vertigo 3")
			assertThat(doc.metadata.author).isEqualTo("W. G. Sebald")
			assertThat(doc.metadata.readingDirection).isEqualTo(ReadingDirection.RTL)
		}
	}

	@Test
	fun coverFitsTheBox() = runTest {
		open(cbz, zip(pages)).use { doc ->
			val cover = doc.cover(360, 540)!!
			assertThat(cover.width).isAtMost(360)
			assertThat(cover.height).isAtMost(540)
			assertThat(cover.getPixel(cover.width / 2, cover.height / 2)).isEqualTo(Color.RED)
		}
	}

	@Test
	fun solidSevenZipReadsForwardAndBack() = runTest {
		open(cb7, sevenZip(pages)).use { doc ->
			assertThat(doc.pageCount).isEqualTo(3)
			assertThat(centre(doc, 2)).isEqualTo(Color.BLUE)
			assertThat(centre(doc, 0)).isEqualTo(Color.RED)
			assertThat(centre(doc, 1)).isEqualTo(Color.GREEN)
		}
	}

	@Test
	fun archiveWithoutImagesIsEmpty() = runTest {
		assertThat(reasonFor(cbz, zip(emptyList(), mapOf("readme.txt" to "hi".toByteArray()))))
			.isEqualTo(BookOpenException.Reason.EMPTY)
	}

	@Test
	fun garbageIsDamaged() = runTest {
		assertThat(reasonFor(cbz, ByteArray(4096) { it.toByte() })).isEqualTo(BookOpenException.Reason.DAMAGED)
		assertThat(reasonFor(cb7, sevenZip(pages).copyOf(64))).isEqualTo(BookOpenException.Reason.DAMAGED)
	}

	@Test
	fun recognisesHeaders() {
		val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
		assertThat(cbz.canOpen(zipHeader, "a.cbz")).isTrue()
		assertThat(cbz.canOpen(zipHeader, "a.epub")).isFalse()
		assertThat(cb7.canOpen(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C), "a.cb7")).isTrue()
	}

	private suspend fun open(format: BookFormat, bytes: ByteArray) = format.open(BytesSource(bytes)) as PagedDocument

	private suspend fun reasonFor(format: BookFormat, bytes: ByteArray): BookOpenException.Reason? =
		(runCatching { format.open(BytesSource(bytes)).close() }.exceptionOrNull() as? BookOpenException)?.reason

	private suspend fun centre(doc: PagedDocument, index: Int): Int {
		val target = pool.acquire(WIDTH / 2, HEIGHT / 2)
		doc.render(index, target)
		return target.getPixel(target.width / 2, target.height / 2).also { pool.release(target) }
	}

	/** Images that are not pages: a macOS resource fork and a hidden file. */
	private fun extras() = listOf("__MACOSX/._page1.png" to Color.BLACK, ".hidden.png" to Color.BLACK)

	private fun png(color: Int): ByteArray {
		val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
		return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
	}

	private fun zip(images: List<Pair<String, Int>>, files: Map<String, ByteArray> = emptyMap()): ByteArray {
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use { zip ->
			(images.map { (name, color) -> name to png(color) } + files.toList()).forEach { (name, bytes) ->
				zip.putNextEntry(ZipEntry(name))
				zip.write(bytes)
				zip.closeEntry()
			}
		}
		return out.toByteArray()
	}

	/** One solid LZMA2 block, the common CB7 layout. */
	private fun sevenZip(images: List<Pair<String, Int>>): ByteArray {
		val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test.cb7")
		SevenZOutputFile(file).use { archive ->
			archive.setContentCompression(SevenZMethod.LZMA2)
			images.forEach { (name, color) ->
				val entry = archive.createArchiveEntry(File(name), name)
				archive.putArchiveEntry(entry)
				archive.write(png(color))
				archive.closeArchiveEntry()
			}
		}
		return file.readBytes().also { file.delete() }
	}

	private companion object {
		const val WIDTH = 200
		const val HEIGHT = 300
	}
}
