package com.entsgra.reader.reader.epub

import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.TextSpan
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Turns one XHTML chapter into [FlowContent]. CSS is ignored: structure comes from the elements
 * (headings, paragraphs, quotes, lists, emphasis), which survives any stylesheet. Whitespace
 * collapses as in a browser, except inside `pre`. A file that breaks off part way keeps what
 * was read before the error.
 *
 * [path] is the chapter's archive path, for resolving links and images; [chapterOf] maps an
 * archive path to its spine index, or null when it is not in the spine.
 */
internal class XhtmlParser(
	private val path: String,
	private val chapterOf: (String) -> Int?,
) {
	private val blocks = mutableListOf<FlowBlock>()
	private val anchors = mutableMapOf<String, Int>()

	/** Length of the blocks already committed. */
	private var committed = 0

	// The paragraph being built: finished spans, plus the run of text in the current style.
	private val spans = mutableListOf<TextSpan>()
	private val run = StringBuilder()
	private var spansLength = 0
	private var pendingSpace = false

	// Inline style, as nesting depths.
	private var bold = 0
	private var italic = 0
	private var superscript = 0
	private val links = ArrayDeque<FlowLink?>()

	// Block context.
	private var heading = 0
	private var pre = 0
	private var quotes = 0
	private val lists = ArrayDeque<ListState>()
	private var skip = 0
	private var svg = 0

	private class ListState(val ordered: Boolean, var next: Int = 1)

	fun parse(bytes: ByteArray): FlowContent {
		try {
			val parser = xmlParser(bytes)
			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				when (parser.eventType) {
					XmlPullParser.START_TAG -> start(parser)
					XmlPullParser.END_TAG -> end(local(parser.name))
					XmlPullParser.TEXT, XmlPullParser.ENTITY_REF, XmlPullParser.CDSECT ->
						if (skip == 0 && svg == 0) text(parser.text.orEmpty())
				}
			}
		} catch (_: XmlPullParserException) {
			// Keep what came before the broken markup.
		}
		flushBlock()
		return FlowContent(blocks.toList(), anchors.toMap())
	}

	private fun start(parser: XmlPullParser) {
		val name = local(parser.name)
		if (skip > 0 || name in SKIPPED) {
			skip++
			return
		}
		if (svg > 0 || name == "svg") {
			svg++
			if (name == "image") image(parser.attr("href"), null)
			return
		}
		if (name in BLOCKS) flushBlock()
		(parser.attr("id") ?: parser.attr("name").takeIf { name == "a" })?.let { anchors.putIfAbsent(it, offset()) }
		when (name) {
			"h1", "h2", "h3", "h4", "h5", "h6" -> heading = name[1].digitToInt()
			"pre" -> pre++
			"blockquote", "dd" -> quotes++
			"ul" -> lists.addLast(ListState(ordered = false))
			"ol" -> lists.addLast(ListState(ordered = true, next = parser.attr("start")?.toIntOrNull() ?: 1))
			"li" -> lists.lastOrNull()?.let { list ->
				val marker = if (list.ordered) "${list.next++}. " else "• "
				run.append(marker)
			}
			"b", "strong" -> styleChange { bold++ }
			"i", "em", "cite", "var", "dfn" -> styleChange { italic++ }
			"sup" -> styleChange { superscript++ }
			"a" -> styleChange { links.addLast(link(parser.attr("href"))) }
			"br" -> lineBreak()
			"hr" -> rule()
			"img" -> image(parser.attr("src"), parser.attr("alt"))
			"td", "th" -> pendingSpace = hasText()
		}
	}

	private fun end(name: String) {
		if (skip > 0) {
			skip--
			return
		}
		if (svg > 0) {
			svg--
			return
		}
		// Relaxed parsing lets stray end tags through; never count below zero.
		when (name) {
			"b", "strong" -> styleChange { bold = (bold - 1).coerceAtLeast(0) }
			"i", "em", "cite", "var", "dfn" -> styleChange { italic = (italic - 1).coerceAtLeast(0) }
			"sup" -> styleChange { superscript = (superscript - 1).coerceAtLeast(0) }
			"a" -> styleChange { links.removeLastOrNull() }
		}
		if (name in BLOCKS) flushBlock()
		when (name) {
			"h1", "h2", "h3", "h4", "h5", "h6" -> heading = 0
			"pre" -> pre = (pre - 1).coerceAtLeast(0)
			"blockquote", "dd" -> quotes = (quotes - 1).coerceAtLeast(0)
			"ul", "ol" -> lists.removeLastOrNull()
		}
	}

	private fun text(value: String) {
		if (pre > 0) {
			run.append(value.replace("\r\n", "\n"))
			return
		}
		for (c in value) {
			// HTML whitespace only: a no-break space is text, not a gap to collapse.
			if (c in HTML_WHITESPACE) {
				pendingSpace = hasText()
			} else {
				if (pendingSpace && !endsWithSpace()) run.append(' ')
				pendingSpace = false
				run.append(c)
			}
		}
	}

	private fun lineBreak() {
		if (!hasText()) return
		run.append('\n')
		pendingSpace = false
	}

	private fun rule() {
		blocks += FlowBlock.Rule
		committed += FlowBlock.Rule.length
	}

	private fun image(href: String?, alt: String?) {
		val key = href?.let { Paths.resolve(path, it) } ?: return
		flushBlock()
		val block = FlowBlock.Image(key, alt?.squeezed()?.ifEmpty { null })
		blocks += block
		committed += block.length
	}

	/** A link inside the book, or null for one that leaves it (or points nowhere readable). */
	private fun link(href: String?): FlowLink? {
		if (href == null) return null
		val target = Paths.resolve(path, href) ?: return null
		val chapter = chapterOf(target) ?: return null
		return FlowLink(chapter, Paths.fragment(href))
	}

	/**
	 * Closes the current text run in the old style before [change] applies the new one. A space
	 * waiting at the boundary goes with the old style, so a link or emphasis starts at its word.
	 */
	private inline fun styleChange(change: () -> Unit) {
		if (pendingSpace && pre == 0 && !endsWithSpace()) {
			run.append(' ')
			pendingSpace = false
		}
		closeRun()
		change()
	}

	private fun closeRun() {
		if (run.isEmpty()) return
		val span = TextSpan(
			text = run.toString(),
			bold = bold > 0,
			italic = italic > 0,
			superscript = superscript > 0,
			link = links.lastOrNull { it != null },
		)
		run.setLength(0)
		val last = spans.lastOrNull()
		if (last != null && last.copy(text = "") == span.copy(text = "")) {
			spans[spans.lastIndex] = last.copy(text = last.text + span.text)
		} else {
			spans += span
		}
		spansLength += span.text.length
	}

	private fun flushBlock() {
		closeRun()
		pendingSpace = false
		// Line breaks at either end would show as blank lines; a space kept for a style change
		// that closed the block is not wanted either.
		val content = spans.mapIndexed { i, span ->
			var text = span.text
			if (i == 0) text = text.trimStart('\n', '\r')
			if (i == spans.lastIndex) text = if (pre > 0) text.trimEnd('\n', '\r') else text.trimEnd('\n', '\r', ' ')
			span.copy(text = text)
		}.filter { it.text.isNotEmpty() }
		spans.clear()
		spansLength = 0
		if (content.none { it.text.isNotBlank() }) return
		val block = if (heading > 0) {
			FlowBlock.Heading(heading, content)
		} else {
			FlowBlock.Paragraph(content, indent = quotes + lists.size, preformatted = pre > 0)
		}
		blocks += block
		committed += block.length
	}

	private fun endsWithSpace() = (run.lastOrNull() ?: spans.lastOrNull()?.text?.lastOrNull()).let { it == ' ' || it == '\n' }

	private fun hasText() = spansLength > 0 || run.isNotEmpty()

	/** Where the next character will land, for anchors. */
	private fun offset() = committed + spansLength + run.length + if (pendingSpace) 1 else 0

	private companion object {
		val HTML_WHITESPACE = charArrayOf(' ', '\t', '\n', '\r', '\u000C')

		val SKIPPED = setOf("head", "script", "style", "template", "noscript")

		/** Elements that start and end a block of their own. */
		val BLOCKS = setOf(
			"p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "blockquote", "pre",
			"section", "article", "header", "footer", "aside", "nav", "main", "figure", "figcaption",
			"table", "tr", "caption", "dl", "dt", "dd", "address", "center", "hgroup", "details",
			"summary", "body",
		)
	}
}
