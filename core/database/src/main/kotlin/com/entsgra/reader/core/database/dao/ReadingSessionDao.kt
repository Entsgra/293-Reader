package com.entsgra.reader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.entsgra.reader.core.database.entity.LogRow
import com.entsgra.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
	@Query("SELECT * FROM reading_sessions ORDER BY started_at DESC")
	fun pageAll(): PagingSource<Int, ReadingSessionEntity>

	@Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY started_at DESC")
	fun pageForBook(bookId: Long): PagingSource<Int, ReadingSessionEntity>

	@Query("SELECT * FROM reading_sessions WHERE physical_book_id = :id ORDER BY started_at DESC")
	fun pageForPhysicalBook(id: Long): PagingSource<Int, ReadingSessionEntity>

	/**
	 * Finished sittings, newest first, with their book's title. Ebooks outside
	 * [visibleFolderIds] (and the root) are left out, so locked folders never show here.
	 */
	@Query(
		"SELECT s.*, COALESCE(b.title, p.title) AS title FROM reading_sessions s " +
			"LEFT JOIN books b ON b.id = s.book_id " +
			"LEFT JOIN physical_books p ON p.id = s.physical_book_id " +
			"WHERE s.ended_at IS NOT NULL " +
			"AND (s.book_id IS NULL OR b.folder_id IS NULL OR b.folder_id IN (:visibleFolderIds)) " +
			"ORDER BY s.started_at DESC",
	)
	fun pageLog(visibleFolderIds: List<Long>): PagingSource<Int, LogRow>

	/** Total milliseconds of finished sittings that started at or after [since]. */
	@Query("SELECT COALESCE(SUM(ended_at - started_at), 0) FROM reading_sessions WHERE ended_at IS NOT NULL AND started_at >= :since")
	fun observeReadingMs(since: Long): Flow<Long>

	@Query("SELECT * FROM reading_sessions WHERE physical_book_id = :id AND ended_at IS NOT NULL ORDER BY started_at DESC")
	fun observeForPhysicalBook(id: Long): Flow<List<ReadingSessionEntity>>

	@Insert
	suspend fun insert(session: ReadingSessionEntity): Long

	@Query(
		"UPDATE reading_sessions SET ended_at = :endedAt, end_page = :endPage, end_progress = :endProgress " +
			"WHERE id = :id",
	)
	suspend fun end(id: Long, endedAt: Long, endPage: Int, endProgress: Float? = null)

	@Query("DELETE FROM reading_sessions WHERE id = :id")
	suspend fun delete(id: Long)
}
