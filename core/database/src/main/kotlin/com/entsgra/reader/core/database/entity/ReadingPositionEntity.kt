package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingPosition

/** Own table so frequent position writes do not invalidate `books` queries. */
@Entity(
	tableName = "reading_positions",
	foreignKeys = [
		ForeignKey(
			entity = BookEntity::class,
			parentColumns = ["id"],
			childColumns = ["book_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
)
data class ReadingPositionEntity(
	@PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
	val locator: Locator,
	val progress: Float,
	@ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun ReadingPositionEntity.asModel() = ReadingPosition(bookId, locator, progress, updatedAt)
