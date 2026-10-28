package com.entsgra.reader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.entsgra.reader.core.database.entity.ReadingSessionEntity

@Dao
interface ReadingSessionDao {
	@Query("SELECT * FROM reading_sessions ORDER BY started_at DESC")
	fun pageAll(): PagingSource<Int, ReadingSessionEntity>

	@Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY started_at DESC")
	fun pageForBook(bookId: Long): PagingSource<Int, ReadingSessionEntity>

	@Query("SELECT * FROM reading_sessions WHERE physical_book_id = :id ORDER BY started_at DESC")
	fun pageForPhysicalBook(id: Long): PagingSource<Int, ReadingSessionEntity>

	@Insert
	suspend fun insert(session: ReadingSessionEntity): Long

	@Query("UPDATE reading_sessions SET ended_at = :endedAt, end_page = :endPage WHERE id = :id")
	suspend fun end(id: Long, endedAt: Long, endPage: Int)

	@Query("DELETE FROM reading_sessions WHERE id = :id")
	suspend fun delete(id: Long)
}
