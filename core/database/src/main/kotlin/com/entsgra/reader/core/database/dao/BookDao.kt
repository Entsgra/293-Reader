package com.entsgra.reader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.entsgra.reader.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
	@Query("SELECT * FROM books WHERE folder_id IS :folderId ORDER BY title COLLATE NOCASE")
	fun pageByTitle(folderId: Long?): PagingSource<Int, BookEntity>

	@Query("SELECT * FROM books WHERE folder_id IS :folderId ORDER BY added_at DESC")
	fun pageByAdded(folderId: Long?): PagingSource<Int, BookEntity>

	@Query(
		"SELECT * FROM books WHERE folder_id IS :folderId " +
			"ORDER BY last_opened_at IS NULL, last_opened_at DESC, title COLLATE NOCASE",
	)
	fun pageByOpened(folderId: Long?): PagingSource<Int, BookEntity>

	/**
	 * Full-text search. [match] is an FTS4 MATCH expression. Only books whose folder is in
	 * [visibleFolderIds] (or at the root) are returned, so locked folders never leak into results.
	 */
	@Query(
		"SELECT books.* FROM books JOIN books_fts ON books.rowid = books_fts.rowid " +
			"WHERE books_fts MATCH :match " +
			"AND (books.folder_id IS NULL OR books.folder_id IN (:visibleFolderIds)) " +
			"ORDER BY books.title COLLATE NOCASE",
	)
	fun search(match: String, visibleFolderIds: List<Long>): PagingSource<Int, BookEntity>

	@Query("SELECT * FROM books WHERE id = :id")
	suspend fun get(id: Long): BookEntity?

	@Query("SELECT * FROM books WHERE id = :id")
	fun observe(id: Long): Flow<BookEntity?>

	@Query("SELECT * FROM books WHERE content_hash = :hash LIMIT 1")
	suspend fun findByHash(hash: String): BookEntity?

	/** Every stored file name, book and cover, for sweeping orphans left by a crash. */
	@Query("SELECT file_name FROM books UNION ALL SELECT cover_file_name FROM books WHERE cover_file_name IS NOT NULL")
	suspend fun allFileNames(): List<String>

	@Insert
	suspend fun insert(book: BookEntity): Long

	@Update
	suspend fun update(book: BookEntity)

	@Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
	suspend fun updateDetails(id: Long, title: String, author: String?)

	@Query("UPDATE books SET folder_id = :folderId WHERE id = :id")
	suspend fun move(id: Long, folderId: Long?)

	@Query("UPDATE books SET last_opened_at = :at WHERE id = :id")
	suspend fun markOpened(id: Long, at: Long)

	@Query("DELETE FROM books WHERE id = :id")
	suspend fun delete(id: Long)
}
