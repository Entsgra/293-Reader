package com.entsgra.reader.core.data.physical

import com.entsgra.reader.core.model.ReadingStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShelfLogicTest {
	private fun details(title: String = "Austerlitz", author: String = "", pages: String = "") =
		PhysicalBookDetails(title, author, pages, ReadingStatus.WANT_TO_READ)

	@Test
	fun detailsAreTrimmedAndBlanksBecomeUnknown() {
		assertThat(checkDetails(details(title = "  Austerlitz ", author = "  ", pages = " ")))
			.isEqualTo(Checked.Ok("Austerlitz", null, null))
		assertThat(checkDetails(details(author = " W. G. Sebald ", pages = "416")))
			.isEqualTo(Checked.Ok("Austerlitz", "W. G. Sebald", 416))
	}

	@Test
	fun blankTitleAndBadPageCountAreRefused() {
		assertThat(checkDetails(details(title = " "))).isEqualTo(Checked.Refused(ShelfCheck.BLANK_TITLE))
		assertThat(checkDetails(details(pages = "0"))).isEqualTo(Checked.Refused(ShelfCheck.BAD_PAGE_COUNT))
		assertThat(checkDetails(details(pages = "12a"))).isEqualTo(Checked.Refused(ShelfCheck.BAD_PAGE_COUNT))
		assertThat(checkDetails(details(pages = "1000000"))).isEqualTo(Checked.Refused(ShelfCheck.BAD_PAGE_COUNT))
	}

	@Test
	fun sittingPagesRunForwardInsideTheBook() {
		assertThat(checkSitting("12", "40", "30", pageCount = 300)).isEqualTo(SittingCheck.Ok(12, 40, 30))
		assertThat(checkSitting("12", "12", "5", pageCount = 300)).isEqualTo(SittingCheck.Ok(12, 12, 5))
		assertThat(checkSitting("40", "12", "30", pageCount = 300)).isEqualTo(SittingCheck.Refused(ShelfCheck.BAD_PAGES))
		assertThat(checkSitting("12", "301", "30", pageCount = 300)).isEqualTo(SittingCheck.Refused(ShelfCheck.BAD_PAGES))
		assertThat(checkSitting("0", "5", "30", pageCount = null)).isEqualTo(SittingCheck.Refused(ShelfCheck.BAD_PAGES))
		assertThat(checkSitting("", "5", "30", pageCount = null)).isEqualTo(SittingCheck.Refused(ShelfCheck.BAD_PAGES))
	}

	@Test
	fun sittingNeedsBetweenAMinuteAndADay() {
		assertThat(checkSitting("1", "5", "0", null)).isEqualTo(SittingCheck.Refused(ShelfCheck.BAD_MINUTES))
		assertThat(checkSitting("1", "5", "1441", null)).isEqualTo(SittingCheck.Refused(ShelfCheck.BAD_MINUTES))
		assertThat(checkSitting("1", "5", " 1440 ", null)).isEqualTo(SittingCheck.Ok(1, 5, 1440))
	}

	@Test
	fun sittingStartsWaitingBooksAndFinishesOnTheLastPage() {
		assertThat(statusAfterSitting(ReadingStatus.WANT_TO_READ, 10, 300)).isEqualTo(ReadingStatus.READING)
		assertThat(statusAfterSitting(ReadingStatus.ABANDONED, 10, 300)).isEqualTo(ReadingStatus.READING)
		assertThat(statusAfterSitting(ReadingStatus.READING, 300, 300)).isEqualTo(ReadingStatus.FINISHED)
		assertThat(statusAfterSitting(ReadingStatus.FINISHED, 10, 300)).isEqualTo(ReadingStatus.FINISHED)
		assertThat(statusAfterSitting(ReadingStatus.READING, 900, null)).isEqualTo(ReadingStatus.READING)
	}
}
