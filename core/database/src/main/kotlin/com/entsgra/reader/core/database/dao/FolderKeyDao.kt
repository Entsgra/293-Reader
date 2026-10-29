package com.entsgra.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.entsgra.reader.core.database.entity.FolderKeyEntity
import com.entsgra.reader.core.model.FolderLock
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FolderKeyDao {
	@Query("SELECT * FROM folder_keys WHERE folder_id = :folderId")
	abstract suspend fun get(folderId: Long): FolderKeyEntity?

	@Query("SELECT * FROM folder_keys")
	abstract suspend fun all(): List<FolderKeyEntity>

	@Query("SELECT * FROM folder_keys")
	abstract fun observeAll(): Flow<List<FolderKeyEntity>>

	@Insert
	protected abstract suspend fun insert(key: FolderKeyEntity)

	@Query("UPDATE folders SET lock = :lock WHERE id = :folderId")
	protected abstract suspend fun setLock(folderId: Long, lock: FolderLock)

	/** The folder is locked from the moment this commits; its books follow one by one. */
	@Transaction
	open suspend fun lock(key: FolderKeyEntity, lock: FolderLock) {
		insert(key)
		setLock(key.folderId, lock)
	}

	/** A new secret for the same keys. */
	@Query(
		"UPDATE folder_keys SET kdf_memory_kib = :memoryKib, kdf_iterations = :iterations, " +
			"kdf_parallelism = :parallelism, salt = :salt, sealed = :sealed WHERE folder_id = :folderId",
	)
	abstract suspend fun rewrap(
		folderId: Long,
		memoryKib: Int,
		iterations: Int,
		parallelism: Int,
		salt: ByteArray,
		sealed: ByteArray,
	)

	@Query("UPDATE folders SET lock = :lock WHERE id = :folderId")
	abstract suspend fun setKind(folderId: Long, lock: FolderLock)

	@Query("UPDATE folder_keys SET biometric = :biometric WHERE folder_id = :folderId")
	abstract suspend fun setBiometric(folderId: Long, biometric: ByteArray?)

	@Query("UPDATE folder_keys SET removing = 1 WHERE folder_id = :folderId")
	abstract suspend fun markRemoving(folderId: Long)

	@Query("DELETE FROM folder_keys WHERE folder_id = :folderId")
	protected abstract suspend fun delete(folderId: Long)

	/** The last book and name are off the key: the folder is plain again. */
	@Transaction
	open suspend fun removeLock(folderId: Long) {
		delete(folderId)
		setLock(folderId, FolderLock.NONE)
	}
}
