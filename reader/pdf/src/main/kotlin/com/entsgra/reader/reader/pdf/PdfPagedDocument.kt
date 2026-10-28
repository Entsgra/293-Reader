package com.entsgra.reader.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import com.entsgra.reader.reader.api.BookMetadata
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.PagedDocument
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One open PDF. [PdfRenderer] allows a single open page at a time and is not thread-safe, so
 * every call holds [lock]. [close] takes the same lock, so it waits for a render in flight
 * instead of freeing the renderer under it.
 */
internal class PdfPagedDocument(private val renderer: PdfRenderer) : PagedDocument {
	private val lock = Any()
	private var closed = false
	private val sizes = arrayOfNulls<PageSize>(renderer.pageCount)

	// PdfRenderer exposes no document info; the title comes from the file name.
	override val metadata = BookMetadata(title = null, author = null)

	override val pageCount: Int = renderer.pageCount

	override suspend fun pageSize(index: Int): PageSize {
		sizes[index]?.let { return it }
		return withContext(Dispatchers.Default) {
			withPage(index) { PageSize(it.width, it.height) }.also { sizes[index] = it }
		}
	}

	override suspend fun render(index: Int, target: Bitmap) = withContext(Dispatchers.Default) {
		withPage(index) { page ->
			// PDF pages are transparent where nothing is drawn; paper is white.
			target.eraseColor(Color.WHITE)
			val matrix = Matrix().apply {
				setScale(target.width / page.width.toFloat(), target.height / page.height.toFloat())
			}
			page.render(target, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
		}
	}

	override suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap? {
		if (pageCount == 0) return null
		val size = pageSize(0)
		val scale = minOf(maxWidth / size.width.toFloat(), maxHeight / size.height.toFloat())
		val bitmap = createBitmap(max(1, (size.width * scale).roundToInt()), max(1, (size.height * scale).roundToInt()))
		render(0, bitmap)
		return bitmap
	}

	override fun close() = synchronized(lock) {
		if (closed) return
		closed = true
		renderer.close() // Also closes the memfd, freeing the plaintext copy.
	}

	private fun <T> withPage(index: Int, block: (PdfRenderer.Page) -> T): T = synchronized(lock) {
		check(!closed) { "Document closed" }
		renderer.openPage(index).use(block)
	}
}
