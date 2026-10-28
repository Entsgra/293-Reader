package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.entsgra.reader.core.model.Bookmark
import com.entsgra.reader.core.model.Locator

@Entity(
	tableName = "bookmarks",
	foreignKeys = [
		ForeignKey(
			entity = BookEntity::class,
			parentColumns = ["id"],
			childColumns = ["book_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index("book_id")],
)
data class BookmarkEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	@ColumnInfo(name = "book_id") val bookId: Long,
	val locator: Locator,
	val label: String?,
	@ColumnInfo(name = "created_at") val createdAt: Long,
)

fun BookmarkEntity.asModel() = Bookmark(id, bookId, locator, label, createdAt)
