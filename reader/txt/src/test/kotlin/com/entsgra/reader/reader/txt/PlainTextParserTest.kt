package com.entsgra.reader.reader.txt

import com.entsgra.reader.reader.api.FlowBlock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlainTextParserTest {
	private fun texts(section: Section) = section.blocks.map { block ->
		when (block) {
			is FlowBlock.Heading -> block.spans.joinToString("") { it.text }
			is FlowBlock.Paragraph -> block.spans.joinToString("") { it.text }
			else -> error("unexpected $block")
		}
	}

	private val filler = "The river ran on past the mill and the old stone bridge. ".repeat(5)

	@Test
	fun utf8WithoutBomDecodesAsUtf8() {
		assertThat(PlainTextParser.decode("Brücke".toByteArray(Charsets.UTF_8))).isEqualTo("Brücke")
	}

	@Test
	fun bomPicksTheEncoding() {
		val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "é".toByteArray(Charsets.UTF_8)
		val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "é".toByteArray(Charsets.UTF_16LE)
		val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "é".toByteArray(Charsets.UTF_16BE)
		assertThat(PlainTextParser.decode(utf8)).isEqualTo("é")
		assertThat(PlainTextParser.decode(le)).isEqualTo("é")
		assertThat(PlainTextParser.decode(be)).isEqualTo("é")
	}

	@Test
	fun invalidUtf8FallsBackToWindows1252() {
		// 0xE9 alone is not UTF-8; in Windows-1252 it is é, and 0x93/0x94 are curly quotes.
		val bytes = byteArrayOf(0x93.toByte(), 'C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte(), 0x94.toByte())
		assertThat(PlainTextParser.decode(bytes)).isEqualTo("“Café”")
	}

	@Test
	fun leadingBomCharacterIsDropped() {
		val text = PlainTextParser.parse("﻿Hello there.")
		assertThat(texts(text.sections.single())).containsExactly("Hello there.")
	}

	@Test
	fun blankLinesSeparateParagraphsAndHardWrapsJoin() {
		val wrapped = (1..6).joinToString("\n") { "This line was wrapped by an editor at a fixed width of sixty-odd chars" }
		val text = PlainTextParser.parse("$wrapped\n\nSecond paragraph.\r\n\r\nThird.")
		val paragraphs = texts(text.sections.single())
		assertThat(paragraphs).hasSize(3)
		assertThat(paragraphs[0]).doesNotContain("\n")
		assertThat(paragraphs[1]).isEqualTo("Second paragraph.")
		assertThat(paragraphs[2]).isEqualTo("Third.")
	}

	@Test
	fun shortLinesKeepTheirBreaks() {
		val prose = (1..10).joinToString("\n\n") {
			"A long line of prose that the editor wrapped at a fixed width here\nand carried on."
		}
		val verse = "Tyger Tyger,\nburning bright,\nIn the forests\nof the night"
		val text = PlainTextParser.parse("$prose\n\n$verse")
		val last = text.sections.single().blocks.last() as FlowBlock.Paragraph
		assertThat(last.spans.single().text).isEqualTo(verse)
		assertThat(last.preformatted).isTrue()
	}

	@Test
	fun filesWithoutBlankLinesTreatEachLineAsAParagraph() {
		val lines = (1..30).joinToString("\n") { "Paragraph $it on its own line." }
		val text = PlainTextParser.parse(lines)
		assertThat(texts(text.sections.single())).hasSize(30)
	}

	@Test
	fun chapterHeadingsStartSections() {
		val text = PlainTextParser.parse("Chapter 1\n\n$filler\n\nCHAPTER TWO\n\n$filler\n\nEpilogue\n\n$filler")
		assertThat(text.sections.map { it.title }).containsExactly("Chapter 1", "CHAPTER TWO", "Epilogue").inOrder()
		assertThat(text.sections[0].blocks.first()).isInstanceOf(FlowBlock.Heading::class.java)
	}

	@Test
	fun contentsListIsNotTakenForChapters() {
		val contents = "Chapter 1\n\nChapter 2\n\nChapter 3"
		val text = PlainTextParser.parse("$contents\n\nChapter 1\n\n$filler\n\nChapter 2\n\n$filler")
		assertThat(text.sections.map { it.title }).containsExactly(null, "Chapter 1", "Chapter 2").inOrder()
	}

	@Test
	fun lowercaseRomanNumeralIsNotAHeading() {
		val text = PlainTextParser.parse("iv\n\n$filler")
		assertThat(text.sections.single().title).isNull()
	}

	@Test
	fun longTextIsCutIntoSectionsAtParagraphs() {
		val paragraph = "x".repeat(1_000)
		val text = PlainTextParser.parse(List(100) { paragraph }.joinToString("\n\n"))
		assertThat(text.sections.size).isGreaterThan(1)
		text.sections.forEach { assertThat(it.length).isAtMost(PlainTextParser.MAX_SECTION_CHARS) }
		assertThat(text.sections.sumOf { it.length }).isEqualTo(100_000)
	}

	@Test
	fun headerGivesTitleAndAuthor() {
		val text = PlainTextParser.parse("Title: Middlemarch\nAuthor: George Eliot\n\n$filler")
		assertThat(text.title).isEqualTo("Middlemarch")
		assertThat(text.author).isEqualTo("George Eliot")
	}

	@Test
	fun blankFileHasNoSections() {
		assertThat(PlainTextParser.parse(" \n\n \t\n").sections).isEmpty()
	}
}
