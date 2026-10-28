package com.entsgra.reader.reader.epub

import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.TextSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XhtmlParserTest {
	init {
		useKxml()
	}

	private val spine = mapOf("OEBPS/ch1.xhtml" to 0, "OEBPS/ch2.xhtml" to 1)

	private fun parse(body: String, head: String = ""): FlowContent {
		val html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n" +
			"<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>$head</head><body>$body</body></html>"
		return XhtmlParser("OEBPS/ch1.xhtml", spine::get).parse(html.toByteArray())
	}

	private fun FlowBlock.text() = when (this) {
		is FlowBlock.Heading -> spans.joinToString("") { it.text }
		is FlowBlock.Paragraph -> spans.joinToString("") { it.text }
		else -> null
	}

	@Test
	fun headingsAndParagraphsBecomeBlocks() {
		val content = parse("<h2>Part One</h2><p>First.</p><p>Second.</p>")
		assertThat(content.blocks[0]).isEqualTo(FlowBlock.Heading(2, listOf(TextSpan("Part One"))))
		assertThat(content.blocks.drop(1).map { it.text() }).containsExactly("First.", "Second.").inOrder()
	}

	@Test
	fun headStylesAndScriptsAreSkipped() {
		val content = parse("<script>var x = 1;</script><p>Text</p>", head = "<title>Ignored</title><style>p{}</style>")
		assertThat(content.blocks.map { it.text() }).containsExactly("Text")
	}

	@Test
	fun whitespaceCollapsesAsInABrowser() {
		val content = parse("<p>\n  Two   words\n and <i>more</i>  </p>")
		assertThat(content.blocks.single().text()).isEqualTo("Two words and more")
	}

	@Test
	fun preKeepsSpacingAndBreaks() {
		val content = parse("<pre>a  b\n  c</pre>")
		val block = content.blocks.single() as FlowBlock.Paragraph
		assertThat(block.preformatted).isTrue()
		assertThat(block.text()).isEqualTo("a  b\n  c")
	}

	@Test
	fun inlineStylesSplitSpans() {
		val content = parse("<p>plain <b>bold <i>both</i></b> note<sup>3</sup></p>")
		assertThat((content.blocks.single() as FlowBlock.Paragraph).spans).containsExactly(
			TextSpan("plain "),
			TextSpan("bold ", bold = true),
			TextSpan("both", bold = true, italic = true),
			TextSpan(" note"),
			TextSpan("3", superscript = true),
		).inOrder()
	}

	@Test
	fun linksInsideTheBookKeepTheirTargetAndOthersArePlain() {
		val content = parse("<p><a href=\"ch2.xhtml#n1\">in</a> <a href=\"https://example.com\">out</a></p>")
		val spans = (content.blocks.single() as FlowBlock.Paragraph).spans
		assertThat(spans.first()).isEqualTo(TextSpan("in", link = FlowLink(1, "n1")))
		assertThat(spans.drop(1).all { it.link == null }).isTrue()
	}

	@Test
	fun anchorsMapToCharacterOffsets() {
		val content = parse("<p>Hello</p><p id=\"second\">World <span id=\"mid\">here</span></p>")
		assertThat(content.anchors["second"]).isEqualTo(5)
		assertThat(content.anchors["mid"]).isEqualTo(11)
		assertThat(content.length).isEqualTo("Hello".length + "World here".length)
	}

	@Test
	fun listsAndQuotesIndentWithMarkers() {
		val content = parse("<blockquote><p>Quoted</p></blockquote><ol start=\"3\"><li>c</li><li>d</li></ol><ul><li>e</li></ul>")
		val blocks = content.blocks.map { it as FlowBlock.Paragraph }
		assertThat(blocks.map { it.text() }).containsExactly("Quoted", "3. c", "4. d", "• e").inOrder()
		assertThat(blocks.map { it.indent }).containsExactly(1, 1, 1, 1).inOrder()
	}

	@Test
	fun imagesAndRulesAreBlocksOfTheirOwn() {
		val content = parse("<p>Before<img src=\"../images/a%20b.png\" alt=\" A map \"/>after</p><hr/><svg><image xlink:href=\"b.jpg\"/></svg>")
		assertThat(content.blocks).containsExactly(
			FlowBlock.Paragraph(listOf(TextSpan("Before"))),
			FlowBlock.Image("images/a b.png", "A map"),
			FlowBlock.Paragraph(listOf(TextSpan("after"))),
			FlowBlock.Rule,
			FlowBlock.Image("OEBPS/b.jpg", null),
		).inOrder()
	}

	@Test
	fun brBreaksTheLineButNotAtTheEdges() {
		val content = parse("<p><br/>one<br/>two<br/></p>")
		assertThat(content.blocks.single().text()).isEqualTo("one\ntwo")
	}

	@Test
	fun htmlEntitiesResolveWithoutADtd() {
		val content = parse("<p>caf&eacute; &mdash; &amp; &#8364;&nbsp;5</p>")
		assertThat(content.blocks.single().text()).isEqualTo("café — & € 5")
	}

	@Test
	fun brokenMarkupKeepsWhatCameBefore() {
		val content = parse("<p>Kept.</p><p>Also <b>kept</b></p><p>broken <<<")
		assertThat(content.blocks.map { it.text() }).containsAtLeast("Kept.", "Also kept").inOrder()
	}

	@Test
	fun strayEndTagsDoNotUnbalanceStyle() {
		val content = parse("<p>a</i></blockquote> <i>b</i></p><p>c</p>")
		val blocks = content.blocks.map { it as FlowBlock.Paragraph }
		assertThat(blocks.last().indent).isEqualTo(0)
		assertThat(blocks.first().spans.last()).isEqualTo(TextSpan("b", italic = true))
	}
}
