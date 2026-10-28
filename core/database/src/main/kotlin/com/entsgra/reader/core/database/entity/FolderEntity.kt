package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderLock

@Entity(
	tableName = "folders",
	foreignKeys = [
		ForeignKey(
			entity = FolderEntity::class,
			parentColumns = ["id"],
			childColumns = ["parent_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index("parent_id")],
)
data class FolderEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	@ColumnInfo(name = "parent_id") val parentId: Long?,
	val name: String,
	val lock: FolderLock = FolderLock.NONE,
	@ColumnInfo(name = "created_at") val createdAt: Long,
)

fun FolderEntity.asModel() = Folder(id, parentId, name, lock, createdAt)
