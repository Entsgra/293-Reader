package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingStatus

@Entity(tableName = "physical_books", indices = [Index("status"), Index("updated_at")])
data class PhysicalBookEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val title: String,
	val author: String?,
	@ColumnInfo(name = "page_count") val pageCount: Int?,
	val isbn: String?,
	val status: ReadingStatus,
	@ColumnInfo(name = "current_page") val currentPage: Int = 0,
	@ColumnInfo(name = "added_at") val addedAt: Long,
	@ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun PhysicalBookEntity.asModel() =
	PhysicalBook(id, title, author, pageCount, isbn, status, currentPage, addedAt, updatedAt)
