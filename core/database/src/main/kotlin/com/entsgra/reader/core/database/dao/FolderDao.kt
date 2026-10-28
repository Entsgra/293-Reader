package com.entsgra.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.entsgra.reader.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
	@Query("SELECT * FROM folders WHERE parent_id IS :parentId ORDER BY name COLLATE NOCASE")
	fun observeChildren(parentId: Long?): Flow<List<FolderEntity>>

	@Query("SELECT * FROM folders WHERE id = :id")
	suspend fun get(id: Long): FolderEntity?

	@Insert
	suspend fun insert(folder: FolderEntity): Long

	@Update
	suspend fun update(folder: FolderEntity)

	@Query("DELETE FROM folders WHERE id = :id")
	suspend fun delete(id: Long)
}
