package com.entsgra.reader.reader.api

import android.graphics.Bitmap
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingDirection

/** An open book. Close it to free native and memory resources. */
sealed interface BookDocument : Releasable {
	val metadata: BookMetadata
}

data class BookMetadata(
	val title: String?,
	val author: String?,
	/** Set when the file says how it reads, such as a manga's ComicInfo; null to keep the default. */
	val readingDirection: ReadingDirection? = null,
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

/**
 * Reflowable text: EPUB, TXT, later MOBI/AZW3/FB2. The format turns each chapter into styled
 * blocks; the reader lays them out, so a position is a chapter and a character offset that
 * survives font and margin changes.
 */
interface FlowDocument : BookDocument {
	/** Table of contents in reading order. Empty when the book has none. */
	val contents: List<TocEntry>

	/** Chapters in reading order (EPUB spine items, TXT sections). Never empty once open. */
	val chapterCount: Int

	/**
	 * Relative length of each chapter, for whole-book progress before every chapter is parsed.
	 * Roughly characters; only the ratios matter.
	 */
	val chapterWeights: List<Long>

	/** BCP 47 language of the text, for hyphenation. Null when the file does not say. */
	val language: String?

	/** Parses chapter [index]. Called lazily, a chapter at a time, off the main thread. */
	suspend fun chapter(index: Int): FlowContent

	/** Natural pixel size of the image [key] from a [FlowBlock.Image], or null if it cannot be read. */
	suspend fun imageSize(key: String): PageSize?

	/** Image [key] decoded to fit within [maxWidth] by [maxHeight]. Null if it cannot be decoded. */
	suspend fun image(key: String, maxWidth: Int, maxHeight: Int): Bitmap?

	suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap?
}

/** A place in a [FlowDocument]: a chapter, and an anchor inside it (an HTML id) or its start. */
data class FlowLink(val chapter: Int, val anchor: String? = null)

data class TocEntry(val title: String, val target: FlowLink, val depth: Int)

/**
 * One chapter as styled blocks. Offsets count characters across the blocks in order: text
 * blocks by their text, images and rules as one each. [anchors] maps HTML ids to offsets.
 */
data class FlowContent(
	val blocks: List<FlowBlock>,
	val anchors: Map<String, Int> = emptyMap(),
) {
	val length: Int = blocks.sumOf { it.length }
}

sealed interface FlowBlock {
	val length: Int

	data class Heading(val level: Int, val spans: List<TextSpan>) : FlowBlock {
		override val length = spans.sumOf { it.text.length }
	}

	/**
	 * Running text. [indent] counts levels of quotes and lists. [preformatted] keeps line breaks
	 * and spacing as written, for poetry and code.
	 */
	data class Paragraph(
		val spans: List<TextSpan>,
		val indent: Int = 0,
		val preformatted: Boolean = false,
	) : FlowBlock {
		override val length = spans.sumOf { it.text.length }
	}

	/** A picture on its own line. [key] is passed back to [FlowDocument.image]. */
	data class Image(val key: String, val alt: String?) : FlowBlock {
		override val length = 1
	}

	/** A scene break or horizontal rule. */
	data object Rule : FlowBlock {
		override val length = 1
	}
}

data class TextSpan(
	val text: String,
	val bold: Boolean = false,
	val italic: Boolean = false,
	/** Footnote markers and the like. */
	val superscript: Boolean = false,
	/** Set for links inside the book; links out of it are plain text. */
	val link: FlowLink? = null,
)
