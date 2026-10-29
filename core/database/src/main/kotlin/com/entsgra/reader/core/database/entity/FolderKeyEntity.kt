package com.entsgra.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A locked folder's keys, sealed: under an Argon2id key from its secret and a Keystore key
 * (`sealed`, with the KDF cost and salt beside it), and optionally under a biometric Keystore
 * key (`biometric`). The row exists for as long as anything is encrypted under the key.
 */
@Entity(
	tableName = "folder_keys",
	foreignKeys = [
		ForeignKey(
			entity = FolderEntity::class,
			parentColumns = ["id"],
			childColumns = ["folder_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
)
class FolderKeyEntity(
	@PrimaryKey @ColumnInfo(name = "folder_id") val folderId: Long,
	@ColumnInfo(name = "kdf_memory_kib") val kdfMemoryKib: Int,
	@ColumnInfo(name = "kdf_iterations") val kdfIterations: Int,
	@ColumnInfo(name = "kdf_parallelism") val kdfParallelism: Int,
	val salt: ByteArray,
	val sealed: ByteArray,
	val biometric: ByteArray?,
	/** The lock is being removed: its books are going back to the app key. */
	@ColumnInfo(defaultValue = "0") val removing: Boolean,
	@ColumnInfo(name = "created_at") val createdAt: Long,
)
