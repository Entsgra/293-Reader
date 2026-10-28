package com.entsgra.reader.feature.physical

import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShelfContentTest {
	private fun book(id: Long, status: ReadingStatus, page: Int = 0, pages: Int? = 200) =
		PhysicalBook(id, "Book $id", null, pages, null, status, page, 0, 0)

	@Test
	fun latestReadingBookGoesOnTheKeyCardOnce() {
		val shelf = shelfContent(
			listOf(
				book(1, ReadingStatus.FINISHED),
				book(2, ReadingStatus.READING),
				book(3, ReadingStatus.READING),
				book(4, ReadingStatus.WANT_TO_READ),
			),
		)
		assertThat(shelf.current?.id).isEqualTo(2)
		assertThat(shelf.groups.map { (status, books) -> status to books.map { it.id } }).containsExactly(
			ReadingStatus.READING to listOf(3L),
			ReadingStatus.WANT_TO_READ to listOf(4L),
			ReadingStatus.FINISHED to listOf(1L),
		).inOrder()
	}

	@Test
	fun emptyShelfIsEmpty() {
		assertThat(shelfContent(emptyList()).isEmpty).isTrue()
		assertThat(shelfContent(listOf(book(1, ReadingStatus.ABANDONED))).current).isNull()
	}

	@Test
	fun percentOnlyForBooksBeingReadWithAKnownLength() {
		assertThat(progressPercent(book(1, ReadingStatus.READING, page = 50))).isEqualTo(25)
		assertThat(progressPercent(book(1, ReadingStatus.READING, page = 50, pages = null))).isNull()
		assertThat(progressPercent(book(1, ReadingStatus.FINISHED, page = 200))).isNull()
		assertThat(progressPercent(book(1, ReadingStatus.READING, page = 0))).isNull()
	}
}
