package com.entsgra.reader.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.PagedDocument
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfFormatTest {
	private val format = PdfFormat()

	@Test
	fun readsPagesAndTheirSizes() = runTest {
		open(samplePdf()).use { doc ->
			assertThat(doc.pageCount).isEqualTo(3)
			assertThat(doc.pageSize(0)).isEqualTo(PageSize(300, 400))
			assertThat(doc.pageSize(2)).isEqualTo(PageSize(500, 250))
		}
	}

	@Test
	fun rendersAPageScaledToTheTarget() = runTest {
		open(samplePdf()).use { doc ->
			val target = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
			doc.render(1, target)
			// Page 2 is filled red; paper outside the drawing would be white.
			assertThat(target.getPixel(300, 400)).isEqualTo(Color.RED)
			assertThat(target.getPixel(2, 2)).isEqualTo(Color.RED)

			doc.render(0, target)
			assertThat(target.getPixel(300, 400)).isEqualTo(Color.WHITE)
		}
	}

	@Test
	fun coverFitsTheBoxAndKeepsTheAspect() = runTest {
		open(samplePdf()).use { doc ->
			val cover = doc.cover(360, 540)!!
			assertThat(cover.width).isEqualTo(360)
			assertThat(cover.height).isEqualTo(480)
		}
	}

	@Test
	fun recognisesThePdfHeader() {
		assertThat(format.canOpen("%PDF-1.7\n".toByteArray(), "x.bin")).isTrue()
		assertThat(format.canOpen("PK\u0003\u0004".toByteArray(), "x.pdf")).isFalse()
	}

	@Test
	fun damagedFileIsReportedAsDamaged() = runTest {
		val broken = samplePdf().copyOf(200)
		val error = runCatching { format.open(BytesSource(broken)) }.exceptionOrNull()
		assertThat(error).isInstanceOf(BookOpenException::class.java)
		assertThat((error as BookOpenException).reason).isEqualTo(BookOpenException.Reason.DAMAGED)
	}

	@Test
	fun closingTwiceIsSafeAndStopsRendering() = runTest {
		val doc = open(samplePdf())
		doc.close()
		doc.close()
		val target = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
		assertThrows(IllegalStateException::class.java) {
			kotlinx.coroutines.runBlocking { doc.render(0, target) }
		}
	}

	private suspend fun open(bytes: ByteArray) = format.open(BytesSource(bytes)) as PagedDocument

	/** Three pages: blank 300x400, red 300x400, blank landscape 500x250. */
	private fun samplePdf(): ByteArray {
		val pdf = PdfDocument()
		val sizes = listOf(300 to 400, 300 to 400, 500 to 250)
		sizes.forEachIndexed { index, (w, h) ->
			val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, index + 1).create())
			if (index == 1) page.canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = Color.RED })
			pdf.finishPage(page)
		}
		return ByteArrayOutputStream().also { pdf.writeTo(it) }.toByteArray().also { pdf.close() }
	}
}
