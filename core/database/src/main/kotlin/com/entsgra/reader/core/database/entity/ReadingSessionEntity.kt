package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.entsgra.reader.core.model.ReadingItem
import com.entsgra.reader.core.model.ReadingSession

/**
 * One log for both kinds of book. Exactly one of [bookId] and [physicalBookId] is set. Pages are
 * 1-based; 0 means the book has no fixed pages (EPUB, TXT), whose sittings record only progress.
 * Progress, 0 to 1 through the book, is kept for ebooks and null for physical books.
 */
@Entity(
	tableName = "reading_sessions",
	foreignKeys = [
		ForeignKey(
			entity = BookEntity::class,
			parentColumns = ["id"],
			childColumns = ["book_id"],
			onDelete = ForeignKey.CASCADE,
		),
		ForeignKey(
			entity = PhysicalBookEntity::class,
			parentColumns = ["id"],
			childColumns = ["physical_book_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index("book_id"), Index("physical_book_id"), Index("started_at")],
)
data class ReadingSessionEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	@ColumnInfo(name = "book_id") val bookId: Long?,
	@ColumnInfo(name = "physical_book_id") val physicalBookId: Long?,
	@ColumnInfo(name = "started_at") val startedAt: Long,
	@ColumnInfo(name = "ended_at") val endedAt: Long?,
	@ColumnInfo(name = "start_page") val startPage: Int,
	@ColumnInfo(name = "end_page") val endPage: Int?,
	@ColumnInfo(name = "start_progress") val startProgress: Float? = null,
	@ColumnInfo(name = "end_progress") val endProgress: Float? = null,
) {
	init {
		require((bookId == null) != (physicalBookId == null)) { "Session needs exactly one book" }
	}

	companion object {
		fun of(item: ReadingItem, startedAt: Long, startPage: Int, startProgress: Float? = null) = ReadingSessionEntity(
			bookId = (item as? ReadingItem.Ebook)?.bookId,
			physicalBookId = (item as? ReadingItem.Physical)?.physicalBookId,
			startedAt = startedAt,
			endedAt = null,
			startPage = startPage,
			endPage = null,
			startProgress = startProgress,
		)
	}
}

fun ReadingSessionEntity.asModel() = ReadingSession(
	id = id,
	item = bookId?.let(ReadingItem::Ebook) ?: ReadingItem.Physical(checkNotNull(physicalBookId)),
	startedAt = startedAt,
	endedAt = endedAt,
	startPage = startPage,
	endPage = endPage,
	startProgress = startProgress,
	endProgress = endProgress,
)
