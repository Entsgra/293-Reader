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
	/** Empty when the name is sealed: then it is in [sealedName], under [keyFolderId]'s key. */
	val name: String,
	val lock: FolderLock = FolderLock.NONE,
	@ColumnInfo(name = "created_at") val createdAt: Long,
	/** The locked folder above this one whose key seals its name; null for a plain name. */
	@ColumnInfo(name = "key_folder_id") val keyFolderId: Long? = null,
	@ColumnInfo(name = "sealed_name") val sealedName: ByteArray? = null,
)

/** With [name] in place of a sealed one, once unsealed. */
fun FolderEntity.asModel(name: String = this.name) = Folder(id, parentId, name, lock, createdAt)
