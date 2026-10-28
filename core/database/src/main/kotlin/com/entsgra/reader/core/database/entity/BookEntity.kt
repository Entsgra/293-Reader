package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.SpreadMode

/**
 * Deleting a folder that still holds books is refused (RESTRICT): books must be moved or deleted,
 * with their files, first. A cascade to SET NULL would surface a locked folder's books at the root.
 */
@Entity(
	tableName = "books",
	foreignKeys = [
		ForeignKey(
			entity = FolderEntity::class,
			parentColumns = ["id"],
			childColumns = ["folder_id"],
			onDelete = ForeignKey.RESTRICT,
		),
	],
	indices = [
		Index("folder_id"),
		Index("content_hash"),
		Index("added_at"),
		Index("last_opened_at"),
	],
)
data class BookEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	@ColumnInfo(name = "folder_id") val folderId: Long?,
	val title: String,
	val author: String?,
	val format: BookFormatId,
	/** Name of the encrypted file in app-private storage. Random; never derived from the title. */
	@ColumnInfo(name = "file_name") val fileName: String,
	@ColumnInfo(name = "file_size") val fileSize: Long,
	/** SHA-256 of the plaintext, hex. Used to spot duplicate imports. */
	@ColumnInfo(name = "content_hash") val contentHash: String,
	@ColumnInfo(name = "cover_file_name") val coverFileName: String?,
	@ColumnInfo(name = "page_count") val pageCount: Int?,
	@ColumnInfo(name = "reading_direction") val readingDirection: ReadingDirection = ReadingDirection.LTR,
	@ColumnInfo(name = "spread_mode") val spreadMode: SpreadMode = SpreadMode.AUTO,
	@ColumnInfo(name = "added_at") val addedAt: Long,
	@ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
)

/** Full-text index over title and author. Room keeps it in sync with `books` via triggers. */
@Fts4(contentEntity = BookEntity::class)
@Entity(tableName = "books_fts")
data class BookFtsEntity(
	val title: String,
	val author: String?,
)

fun BookEntity.asModel() = Book(
	id = id,
	folderId = folderId,
	title = title,
	author = author,
	format = format,
	fileName = fileName,
	fileSize = fileSize,
	contentHash = contentHash,
	coverFileName = coverFileName,
	pageCount = pageCount,
	readingDirection = readingDirection,
	spreadMode = spreadMode,
	addedAt = addedAt,
	lastOpenedAt = lastOpenedAt,
)
