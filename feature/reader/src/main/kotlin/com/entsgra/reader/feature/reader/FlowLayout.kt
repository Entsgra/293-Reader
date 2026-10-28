package com.entsgra.reader.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.TextSpan
import kotlin.math.roundToInt

/**
 * One screen of a chapter. [start] and [end] are chapter offsets (see [FlowContent]): the page
 * shows characters from [start] up to, not including, [end].
 */
internal class FlowPage(val chapter: Int, val start: Int, val end: Int, val pieces: List<FlowPiece>) {
	/** The in-book link at ([x], [y]) in text-area pixels, if any. */
	fun linkAt(x: Float, y: Float, slop: Float): FlowLink? =
		pieces.firstNotNullOfOrNull { (it as? FlowPiece.Text)?.linkAt(x, y, slop) }
}

/** Something drawn on a [FlowPage], [top] pixels below the top of the text area. */
internal sealed interface FlowPiece {
	val top: Int

	/**
	 * Lines [firstLine] to [lastLine] of a laid-out block. One layout is shared by every page the
	 * block spans, so the lines break exactly as measured.
	 */
	class Text(
		val layout: TextLayoutResult,
		val firstLine: Int,
		val lastLine: Int,
		val indent: Int,
		override val top: Int,
	) : FlowPiece {
		val height: Int get() = (layout.getLineBottom(lastLine) - layout.getLineTop(firstLine)).roundToInt()

		/**
		 * The link under ([x], [y]) in page coordinates, if any. Glyphs within [slop] pixels
		 * count, so a footnote number is not a pin-point target.
		 */
		fun linkAt(x: Float, y: Float, slop: Float): FlowLink? {
			if (y < top - slop || y >= top + height + slop) return null
			val local = Offset(x - indent, y - top + layout.getLineTop(firstLine))
			val boundary = layout.getOffsetForPosition(local)
			val text = layout.layoutInput.text
			// The boundary nearest the finger: the glyph is the one before or after it.
			return listOf(boundary - 1, boundary)
				.filter { it in text.indices && layout.getLineForOffset(it) in firstLine..lastLine }
				.map { it to layout.getBoundingBox(it).inflate(slop) }
				.filter { (_, box) -> box.contains(local) }
				.firstNotNullOfOrNull { (offset, _) ->
					text.getStringAnnotations(LINK_TAG, offset, offset + 1).firstOrNull()?.item?.let(::decodeLink)
				}
		}
	}

	class Image(val key: String, val width: Int, val height: Int, override val top: Int) : FlowPiece

	class Rule(override val top: Int, val height: Int) : FlowPiece
}

/** Pixel sizes and styles a chapter is laid out with. */
internal data class FlowMetrics(
	val width: Int,
	val height: Int,
	val body: TextStyle,
	val heading: TextStyle,
	val subheading: TextStyle,
	/** Height of one line of body text. */
	val bodyLine: Int,
	/** Space between blocks. */
	val blockGap: Int,
	/** Indent per level of quote or list. */
	val indentStep: Int,
	/** Height given to a scene break. */
	val ruleHeight: Int,
	/** Book pixels to screen pixels for images. */
	val imageScale: Float,
)

/**
 * Lays [content] out into pages of [FlowMetrics.width] by [FlowMetrics.height]. Blocks flow on
 * from page to page, split between lines; a heading never sits alone at the foot of a page, and
 * a picture too tall for the space left starts the next page, shrunk to fit if it must. Always
 * returns at least one page, so an empty chapter is still a place to stand.
 */
internal suspend fun paginate(
	chapter: Int,
	content: FlowContent,
	metrics: FlowMetrics,
	measurer: TextMeasurer,
	imageSize: suspend (String) -> PageSize?,
): List<FlowPage> {
	val pages = mutableListOf<FlowPage>()
	var pieces = mutableListOf<FlowPiece>()
	var y = 0
	var pageStart = 0

	fun newPage(nextStart: Int) {
		if (pieces.isNotEmpty()) pages += FlowPage(chapter, pageStart, nextStart, pieces)
		pieces = mutableListOf()
		y = 0
		pageStart = nextStart
	}

	var offset = 0
	for (block in content.blocks) {
		when (block) {
			is FlowBlock.Heading, is FlowBlock.Paragraph -> {
				val style = when {
					block is FlowBlock.Heading && block.level <= 2 -> metrics.heading
					block is FlowBlock.Heading -> metrics.subheading
					else -> metrics.body
				}
				val indent = ((block as? FlowBlock.Paragraph)?.indent ?: 0).coerceAtMost(MAX_INDENT) * metrics.indentStep
				val spans = if (block is FlowBlock.Heading) block.spans else (block as FlowBlock.Paragraph).spans
				val layout = measurer.measure(
					text = annotate(spans),
					style = style,
					constraints = Constraints(maxWidth = (metrics.width - indent).coerceAtLeast(1)),
				)
				val heading = block is FlowBlock.Heading
				// Keep a heading with at least two lines of what follows.
				val keep = if (heading) metrics.bodyLine * 2 else 0
				var line = 0
				var gap = if (y > 0) metrics.blockGap * (if (heading) 2 else 1) else 0
				while (line < layout.lineCount) {
					val top = layout.getLineTop(line)
					val room = metrics.height - y - gap
					if (heading && line == 0 && y > 0 && layout.size.height + keep > room) {
						newPage(offset)
						gap = 0
						continue
					}
					var last = line
					while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= room) last++
					if (layout.getLineBottom(line) - top > room && y > 0) {
						newPage(offset + layout.getLineStart(line))
						gap = 0
						continue
					}
					y += gap
					gap = 0
					val piece = FlowPiece.Text(layout, line, last, indent, y)
					pieces += piece
					y += piece.height
					line = last + 1
					if (line < layout.lineCount) newPage(offset + layout.getLineStart(line))
				}
			}

			is FlowBlock.Image -> {
				val natural = imageSize(block.key)
				if (natural != null) {
					var width = (natural.width * metrics.imageScale).coerceAtMost(metrics.width.toFloat())
					var height = width * natural.height / natural.width
					if (height > metrics.height) {
						width *= metrics.height / height
						height = metrics.height.toFloat()
					}
					val gap = if (y > 0) metrics.blockGap else 0
					if (y > 0 && y + gap + height > metrics.height) newPage(offset)
					y += if (y > 0) gap else 0
					pieces += FlowPiece.Image(block.key, width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1), y)
					y += height.roundToInt()
				}
			}

			FlowBlock.Rule -> {
				if (y > 0 && y + metrics.ruleHeight > metrics.height) newPage(offset)
				if (y > 0) {
					pieces += FlowPiece.Rule(y, metrics.ruleHeight)
					y += metrics.ruleHeight
				}
			}
		}
		offset += block.length
	}
	newPage(content.length)
	if (pages.isEmpty()) pages += FlowPage(chapter, 0, content.length, emptyList())
	return pages
}

/** The page of [pages] that shows chapter offset [offset]. */
internal fun List<FlowPage>.indexOfOffset(offset: Int): Int {
	val index = indexOfLast { it.start <= offset }
	return index.coerceAtLeast(0)
}

/** Styled spans as one string whose characters line up with the chapter offsets. */
internal fun annotate(spans: List<TextSpan>): AnnotatedString = buildAnnotatedString {
	for (span in spans) {
		val style = SpanStyle(
			fontWeight = if (span.bold) FontWeight.SemiBold else null,
			fontStyle = if (span.italic) FontStyle.Italic else null,
			baselineShift = if (span.superscript) BaselineShift.Superscript else null,
			fontSize = if (span.superscript) SUPERSCRIPT_SIZE.em else TextUnit.Unspecified,
			textDecoration = if (span.link != null) TextDecoration.Underline else null,
		)
		val link = span.link
		if (link != null) pushStringAnnotation(LINK_TAG, encodeLink(link))
		withStyle(style) { append(span.text) }
		if (link != null) pop()
	}
}

private fun encodeLink(link: FlowLink) = "${link.chapter}#${link.anchor.orEmpty()}"

private fun decodeLink(value: String): FlowLink? {
	val chapter = value.substringBefore('#').toIntOrNull() ?: return null
	return FlowLink(chapter, value.substringAfter('#').ifEmpty { null })
}

private const val LINK_TAG = "link"
private const val SUPERSCRIPT_SIZE = 0.7f

/** Deeper nesting than this keeps the same indent, so text never gets squeezed into a sliver. */
private const val MAX_INDENT = 4
