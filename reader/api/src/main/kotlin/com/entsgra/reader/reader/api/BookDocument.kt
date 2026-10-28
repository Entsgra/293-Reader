package com.entsgra.reader.reader.api

import android.graphics.Bitmap
import com.entsgra.reader.core.model.Locator

/** An open book. Close it to free native and memory resources. */
sealed interface BookDocument : Releasable {
	val metadata: BookMetadata
}

data class BookMetadata(
	val title: String?,
	val author: String?,
)

/** Fixed pages: PDF and comics. */
interface PagedDocument : BookDocument {
	val pageCount: Int

	/** Natural size of a page in pixels at 1x, for layout before rendering. */
	suspend fun pageSize(index: Int): PageSize

	/**
	 * Renders page [index] scaled to fill [target]. [target] comes from the caller's bitmap pool;
	 * the document never allocates page bitmaps itself.
	 */
	suspend fun render(index: Int, target: Bitmap)

	/** A small image for the library grid. */
	suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap?
}

data class PageSize(val width: Int, val height: Int)

/** Reflowable text: EPUB, TXT, later MOBI/AZW3/FB2. Laid out by the reader, not the format. */
interface FlowDocument : BookDocument {
	val chapters: List<TocEntry>

	/** Number of spine items. Chapters in the TOC may point anywhere inside them. */
	val spineSize: Int

	/** Parses one spine item into styled blocks. Called lazily, one item at a time. */
	suspend fun spineItem(index: Int): FlowContent

	suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap?
}

data class TocEntry(val title: String, val locator: Locator.Text, val depth: Int)

/** Format-neutral styled text. Kept small on purpose; grows with the EPUB reader in Phase 4. */
data class FlowContent(val blocks: List<FlowBlock>)

sealed interface FlowBlock {
	data class Heading(val level: Int, val text: String) : FlowBlock
	data class Paragraph(val spans: List<TextSpan>) : FlowBlock
	data class Image(val key: String, val alt: String?) : FlowBlock
	data object Break : FlowBlock
}

data class TextSpan(
	val text: String,
	val bold: Boolean = false,
	val italic: Boolean = false,
	val href: String? = null,
)
