package com.entsgra.reader.reader.txt

import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.TextSpan
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A run of the text read as one chapter. [title] is set when it starts at a detected heading. */
internal data class Section(val title: String?, val blocks: List<FlowBlock>) {
	val length: Int = blocks.sumOf { it.length }
}

/** A plain-text book split into sections, with any title and author found in its header. */
internal data class PlainText(val title: String?, val author: String?, val sections: List<Section>)

/**
 * Turns a plain-text file into paragraphs and chapters. Text files have no markup, so the
 * structure is inferred:
 *
 * - Paragraphs are separated by blank lines, or are single lines when the file has no blank lines.
 * - Lines hard-wrapped at a fixed width (Project Gutenberg wraps at about 70) are joined; short
 *   lines inside a paragraph, such as verse, keep their breaks.
 * - A short line on its own reading "Chapter 12", "PART TWO", "Prologue" or a Roman numeral
 *   starts a chapter, unless no text follows it before the next one, as in a contents list.
 * - Chapters longer than [MAX_SECTION_CHARS] are cut at a paragraph so no section is slow to lay out.
 */
internal object PlainTextParser {
	const val MAX_SECTION_CHARS = 40_000

	/** A heading needs at least this much text after it, so contents lists stay plain lines. */
	private const val MIN_CHAPTER_CHARS = 200
	private const val MAX_HEADING_CHARS = 60

	/** A line at least this share of the wrap width was wrapped, not broken by the author. */
	private const val WRAPPED_SHARE = 0.7

	/** Below this wrap width, short lines are the author's (verse), not the editor's. */
	private const val MIN_WRAP_WIDTH = 40

	private const val NUMBER = "(\\d+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|" +
		"thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|" +
		"first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|last|the \\w+)"
	private val HEADING = Regex(
		"^(chapter|part|book|volume|act)\\s+$NUMBER\\b.*" +
			"|^(prologue|epilogue|introduction|preface|foreword|afterword|appendix)\\b.*" +
			"|^[IVXLC]+\\.?$",
		RegexOption.IGNORE_CASE,
	)
	private val ROMAN = Regex("^[ivxlc]+\\.?$")
	private val HEADER_FIELD = Regex("^(Title|Author):\\s*(.+)$")
	private const val HEADER_LINES = 60

	fun parse(raw: String): PlainText {
		val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
		val lines = text.split('\n').map { it.trimEnd() }
		val header = lines.take(HEADER_LINES).mapNotNull { HEADER_FIELD.find(it.trim())?.destructured }
		val paragraphs = paragraphs(lines)
		return PlainText(
			title = header.firstOrNull { it.component1() == "Title" }?.component2(),
			author = header.firstOrNull { it.component1() == "Author" }?.component2(),
			sections = sections(paragraphs),
		)
	}

	/** Paragraph texts, with hard wraps joined. */
	private fun paragraphs(lines: List<String>): List<String> {
		val blank = lines.count { it.isBlank() }
		val filled = lines.size - blank
		// Some files put every paragraph on one line with no blank lines between.
		if (blank * 20 < filled) return lines.filter { it.isNotBlank() }.map { it.trim() }

		val groups = mutableListOf<List<String>>()
		var current = mutableListOf<String>()
		for (line in lines) {
			if (line.isBlank()) {
				if (current.isNotEmpty()) groups += current
				current = mutableListOf()
			} else {
				current += line.trim()
			}
		}
		if (current.isNotEmpty()) groups += current

		val wrapWidth = wrapWidth(groups)
		return groups.map { group ->
			buildString {
				group.forEachIndexed { i, line ->
					if (i > 0) {
						val wrapped = wrapWidth >= MIN_WRAP_WIDTH && group[i - 1].length >= wrapWidth * WRAPPED_SHARE
						append(if (wrapped) ' ' else '\n')
					}
					append(line)
				}
			}
		}
	}

	/** The width most continued lines reach: the 90th percentile of lines followed by another. */
	private fun wrapWidth(groups: List<List<String>>): Int {
		val lengths = groups.flatMap { it.dropLast(1) }.map { it.length }.sorted()
		if (lengths.isEmpty()) return 0
		return lengths[(lengths.size * 9 / 10).coerceAtMost(lengths.lastIndex)]
	}

	private fun sections(paragraphs: List<String>): List<Section> {
		val headings = headingIndices(paragraphs)
		val sections = mutableListOf<Section>()
		var title: String? = null
		var blocks = mutableListOf<FlowBlock>()
		var length = 0

		fun close() {
			if (blocks.isNotEmpty()) sections += Section(title, blocks)
			title = null
			blocks = mutableListOf()
			length = 0
		}

		paragraphs.forEachIndexed { i, paragraph ->
			if (i in headings) {
				close()
				title = paragraph
				blocks += FlowBlock.Heading(2, listOf(TextSpan(paragraph)))
				length += paragraph.length
				return@forEachIndexed
			}
			if (length + paragraph.length > MAX_SECTION_CHARS && length > 0) close()
			// One paragraph longer than a section is still one block; the layout pages it.
			blocks += FlowBlock.Paragraph(listOf(TextSpan(paragraph)), preformatted = '\n' in paragraph)
			length += paragraph.length
		}
		close()
		return sections
	}

	private fun headingIndices(paragraphs: List<String>): Set<Int> {
		val candidates = paragraphs.indices.filter { i ->
			val p = paragraphs[i]
			// Lowercase "i." or "v" alone is more likely a list marker than a chapter.
			p.length <= MAX_HEADING_CHARS && '\n' !in p && HEADING.matches(p) && !ROMAN.matches(p)
		}
		return candidates.withIndex().filter { (n, i) ->
			val next = candidates.getOrNull(n + 1) ?: paragraphs.size
			(i + 1 until next).sumOf { paragraphs[it].length } >= MIN_CHAPTER_CHARS
		}.map { it.value }.toSet()
	}

	/**
	 * Decodes [bytes]: by byte-order mark when there is one, else as UTF-8 when it is valid
	 * UTF-8, else as Windows-1252, the usual encoding of older Western text files.
	 */
	fun decode(bytes: ByteArray): String = when {
		bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
		bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
		bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
		else -> try {
			Charsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString()
		} catch (_: CharacterCodingException) {
			String(bytes, Charset.forName("windows-1252"))
		}
	}

	private fun ByteArray.startsWith(vararg prefix: Int) =
		size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
