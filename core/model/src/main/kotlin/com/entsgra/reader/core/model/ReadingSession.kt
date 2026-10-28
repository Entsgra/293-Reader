package com.entsgra.reader.core.model

/**
 * One sitting with a book. Shared by ebooks and physical books: exactly one of the two ids is set.
 * Pages are 1-based, 0 for books without fixed pages (EPUB, TXT). Progress (0 to 1) is recorded
 * for ebooks only.
 */
data class ReadingSession(
	val id: Long,
	val item: ReadingItem,
	val startedAt: Long,
	val endedAt: Long?,
	val startPage: Int,
	val endPage: Int?,
	val startProgress: Float? = null,
	val endProgress: Float? = null,
)

sealed interface ReadingItem {
	data class Ebook(val bookId: Long) : ReadingItem
	data class Physical(val physicalBookId: Long) : ReadingItem
}
