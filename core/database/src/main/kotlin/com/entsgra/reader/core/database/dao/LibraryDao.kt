package com.entsgra.reader.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Multi-statement library changes that must apply all at once. */
@Dao
abstract class LibraryDao {
	/** [folderId] and every folder below it. */
	@Query(
		"WITH RECURSIVE sub(id) AS (" +
			"SELECT id FROM folders WHERE id = :folderId " +
			"UNION ALL SELECT f.id FROM folders f JOIN sub ON f.parent_id = sub.id) " +
			"SELECT id FROM sub",
	)
	abstract suspend fun subtreeIds(folderId: Long): List<Long>

	@Query("SELECT file_name, cover_file_name FROM books WHERE folder_id IN (:folderIds)")
	protected abstract suspend fun filesIn(folderIds: List<Long>): List<StoredFiles>

	@Query("SELECT file_name, cover_file_name FROM books WHERE id = :bookId")
	protected abstract suspend fun filesOf(bookId: Long): StoredFiles?

	@Query("DELETE FROM books WHERE folder_id IN (:folderIds)")
	protected abstract suspend fun deleteBooksIn(folderIds: List<Long>)

	@Query("DELETE FROM books WHERE id = :bookId")
	protected abstract suspend fun deleteBookRow(bookId: Long)

	/** Child folders go with it through the parent_id cascade. */
	@Query("DELETE FROM folders WHERE id = :folderId")
	protected abstract suspend fun deleteFolderRow(folderId: Long)

	@Query("UPDATE folders SET parent_id = :parentId WHERE id = :folderId")
	protected abstract suspend fun setParent(folderId: Long, parentId: Long?)

	/**
	 * Deletes a folder, everything below it and their books in one transaction. Returns the
	 * files to remove from storage; the caller deletes them after the commit.
	 */
	@Transaction
	open suspend fun deleteFolderTree(folderId: Long): List<StoredFiles> {
		val ids = subtreeIds(folderId)
		if (ids.isEmpty()) return emptyList()
		val files = ids.chunked(MAX_VARS).flatMap { filesIn(it) }
		ids.chunked(MAX_VARS).forEach { deleteBooksIn(it) }
		deleteFolderRow(folderId)
		return files
	}

	@Transaction
	open suspend fun deleteBook(bookId: Long): StoredFiles? {
		val files = filesOf(bookId) ?: return null
		deleteBookRow(bookId)
		return files
	}

	/** Moves a folder under [parentId] (null: the root). Refuses to move it into itself. */
	@Transaction
	open suspend fun moveFolder(folderId: Long, parentId: Long?): Boolean {
		if (parentId != null && parentId in subtreeIds(folderId)) return false
		setParent(folderId, parentId)
		return true
	}

	private companion object {
		/** SQLite's bound-variable limit is 999 on older platform builds. */
		const val MAX_VARS = 900
	}
}

data class StoredFiles(
	@ColumnInfo(name = "file_name") val fileName: String,
	@ColumnInfo(name = "cover_file_name") val coverFileName: String?,
)
