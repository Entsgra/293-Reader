package com.entsgra.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.entsgra.reader.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
	@Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY created_at")
	fun observe(bookId: Long): Flow<List<BookmarkEntity>>

	@Insert
	suspend fun insert(bookmark: BookmarkEntity): Long

	@Query("DELETE FROM bookmarks WHERE id = :id")
	suspend fun delete(id: Long)
}
