package com.entsgra.reader.reader.txt

import android.graphics.Bitmap
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookFormat
import com.entsgra.reader.reader.api.BookMetadata
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.BookSource
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowDocument
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.TocEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain text in UTF-8, UTF-16 or Windows-1252. Decoded and split into sections once, on open. */
class TxtFormat @Inject constructor() : BookFormat {
	override val id = BookFormatId.TXT

	override fun canOpen(header: ByteArray, fileName: String): Boolean =
		fileName.substringAfterLast('.').lowercase() == "txt"

	override suspend fun open(source: BookSource): BookDocument {
		if (source.size > MAX_BYTES) throw BookOpenException(BookOpenException.Reason.TOO_LARGE)
		val bytes = withContext(Dispatchers.IO) {
			try {
				source.openStream().use { it.readCapped() }
			} catch (e: IOException) {
				throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
			}
		}
		val text = withContext(Dispatchers.Default) { PlainTextParser.parse(PlainTextParser.decode(bytes)) }
		if (text.sections.isEmpty()) throw BookOpenException(BookOpenException.Reason.EMPTY)
		return TxtDocument(text)
	}

	private fun InputStream.readCapped(): ByteArray {
		val out = ByteArrayOutputStream()
		val buffer = ByteArray(BUFFER_BYTES)
		while (true) {
			val n = read(buffer)
			if (n < 0) break
			if (out.size() + n > MAX_BYTES) throw BookOpenException(BookOpenException.Reason.TOO_LARGE)
			out.write(buffer, 0, n)
		}
		return out.toByteArray()
	}

	private companion object {
		/** About 16 million characters, several times the longest novels. Held in memory as text. */
		const val MAX_BYTES = 16L * 1024 * 1024
		const val BUFFER_BYTES = 64 * 1024
	}
}

internal class TxtDocument(text: PlainText) : FlowDocument {
	private val sections = text.sections

	override val metadata = BookMetadata(title = text.title, author = text.author)

	override val contents: List<TocEntry> = sections.withIndex()
		.mapNotNull { (i, section) -> section.title?.let { TocEntry(it, FlowLink(i), depth = 0) } }

	override val chapterCount = sections.size

	override val chapterWeights: List<Long> = sections.map { it.length.toLong().coerceAtLeast(1L) }

	override val language: String? = null

	override suspend fun chapter(index: Int) = FlowContent(sections[index].blocks)

	override suspend fun imageSize(key: String): PageSize? = null

	override suspend fun image(key: String, maxWidth: Int, maxHeight: Int): Bitmap? = null

	override suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap? = null

	override fun close() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TxtModule {
	@Binds
	@IntoSet
	fun bindTxt(format: TxtFormat): BookFormat
}
