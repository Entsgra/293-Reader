package com.entsgra.reader.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.entsgra.reader.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
	@Query("SELECT * FROM folders WHERE parent_id IS :parentId ORDER BY name COLLATE NOCASE")
	fun observeChildren(parentId: Long?): Flow<List<FolderEntity>>

	/** Child folders with the number of books and folders directly inside each. */
	@Query(
		"SELECT f.*, " +
			"(SELECT COUNT(*) FROM books b WHERE b.folder_id = f.id) + " +
			"(SELECT COUNT(*) FROM folders c WHERE c.parent_id = f.id) AS item_count " +
			"FROM folders f WHERE f.parent_id IS :parentId ORDER BY f.name COLLATE NOCASE",
	)
	fun observeChildrenWithCounts(parentId: Long?): Flow<List<FolderWithCount>>

	@Query("SELECT * FROM folders WHERE id = :id")
	fun observe(id: Long): Flow<FolderEntity?>

	/** Every folder. Folder counts stay small, so the tree is assembled in memory. */
	@Query("SELECT * FROM folders")
	fun observeAll(): Flow<List<FolderEntity>>

	@Query("SELECT * FROM folders")
	suspend fun all(): List<FolderEntity>

	@Query("SELECT * FROM folders WHERE id = :id")
	suspend fun get(id: Long): FolderEntity?

	@Query(
		"SELECT COUNT(*) FROM folders WHERE parent_id IS :parentId " +
			"AND name = :name COLLATE NOCASE AND id != :exceptId",
	)
	suspend fun countSiblingsNamed(parentId: Long?, name: String, exceptId: Long = 0): Int

	/** Books and folders below the root, or below [folderId] and in it. */
	@Query(
		"WITH RECURSIVE sub(id) AS (" +
			"SELECT id FROM folders WHERE id = :folderId " +
			"UNION ALL SELECT f.id FROM folders f JOIN sub ON f.parent_id = sub.id) " +
			"SELECT (SELECT COUNT(*) FROM books WHERE folder_id IN (SELECT id FROM sub)) AS books, " +
			"(SELECT COUNT(*) FROM sub) - 1 AS folders",
	)
	fun observeSubtreeCounts(folderId: Long): Flow<CountsRow>

	@Query("SELECT (SELECT COUNT(*) FROM books) AS books, (SELECT COUNT(*) FROM folders) AS folders")
	fun observeLibraryCounts(): Flow<CountsRow>

	@Insert
	suspend fun insert(folder: FolderEntity): Long

	@Query("UPDATE folders SET name = :name WHERE id = :id")
	suspend fun rename(id: Long, name: String)
}

data class FolderWithCount(
	@Embedded val folder: FolderEntity,
	@ColumnInfo(name = "item_count") val itemCount: Int,
)

data class CountsRow(val books: Int, val folders: Int)
