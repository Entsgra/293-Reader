package com.entsgra.reader.core.model

/** One sitting with a book. Shared by ebooks and physical books: exactly one of the two ids is set. */
data class ReadingSession(
	val id: Long,
	val item: ReadingItem,
	val startedAt: Long,
	val endedAt: Long?,
	val startPage: Int,
	val endPage: Int?,
)

sealed interface ReadingItem {
	data class Ebook(val bookId: Long) : ReadingItem
	data class Physical(val physicalBookId: Long) : ReadingItem
}
