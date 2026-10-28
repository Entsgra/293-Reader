package com.entsgra.reader.feature.reader

import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.TextSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlowProgressTest {
	private val progress = FlowProgress(listOf(100L, 300L, 600L))

	private fun pages(chapter: Int, vararg starts: Int, length: Int) =
		starts.mapIndexed { i, start -> FlowPage(chapter, start, starts.getOrElse(i + 1) { length }, emptyList()) }

	private val content = FlowContent(
		blocks = listOf(FlowBlock.Paragraph(listOf(TextSpan("x".repeat(1000))))),
		anchors = mapOf("note" to 450),
	)

	/** Chapter 1, 1000 characters long, pages starting at 0, 400 and 800, with neighbours either side. */
	private val window = FlowWindow(
		chapter = 1,
		content = content,
		pages = pages(1, 0, 400, 800, length = 1000),
		previous = FlowPage(0, 90, 100, emptyList()),
		next = FlowPage(2, 0, 50, emptyList()),
	)

	@Test
	fun progressCountsEarlierChaptersAndTheEndOfThePage() {
		assertThat(progress.startOf(1)).isWithin(1e-6f).of(0.1f)
		// End of the second page of chapter 1: 100 + 300 * 0.8.
		assertThat(progress.at(window, window.pages[1])).isWithin(1e-6f).of(0.34f)
		assertThat(progress.at(window, window.pages.last())).isWithin(1e-6f).of(0.4f)
	}

	@Test
	fun locateFindsTheChapterAndFractionThrough() {
		val (chapter, fraction) = progress.locate(0.7f)
		assertThat(chapter).isEqualTo(2)
		assertThat(fraction).isWithin(1e-6f).of(0.5f)
		assertThat(progress.locate(0f)).isEqualTo(0 to 0f)
		assertThat(progress.locate(1f).first).isEqualTo(2)
	}

	@Test
	fun overallFallsBackToTheChapterStartBeforeLayout() {
		assertThat(progress.overall(Locator.Text(2, 10), null)).isWithin(1e-6f).of(0.4f)
		assertThat(progress.overall(Locator.Text(1, 500), window)).isWithin(1e-6f).of(0.34f)
	}

	@Test
	fun windowIndicesSkipThePreviousChapterPage() {
		assertThat(window.items).hasSize(5)
		assertThat(window.indexOf(FlowEntry.Start)).isEqualTo(1)
		assertThat(window.indexOf(FlowEntry.End)).isEqualTo(3)
		assertThat(window.indexOf(FlowEntry.At(399))).isEqualTo(1)
		assertThat(window.indexOf(FlowEntry.At(400))).isEqualTo(2)
		assertThat(window.indexOf(FlowEntry.Anchor("note"))).isEqualTo(2)
		assertThat(window.indexOf(FlowEntry.Anchor("missing"))).isEqualTo(1)
		assertThat(window.indexOf(FlowEntry.Fraction(0.85f))).isEqualTo(3)
	}

	@Test
	fun firstChapterHasNoPageBefore() {
		val first = FlowWindow(0, content, pages(0, 0, 10, length = 20), previous = null, next = null)
		assertThat(first.indexOf(FlowEntry.Start)).isEqualTo(0)
		assertThat(first.items).hasSize(2)
	}
}
