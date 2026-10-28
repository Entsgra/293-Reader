package com.entsgra.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.entsgra.reader.core.database.entity.PhysicalBookEntity
import com.entsgra.reader.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PhysicalBookDao {
	@Query("SELECT * FROM physical_books WHERE status = :status ORDER BY updated_at DESC")
	fun observeByStatus(status: ReadingStatus): Flow<List<PhysicalBookEntity>>

	@Query("SELECT * FROM physical_books ORDER BY updated_at DESC")
	fun observeAll(): Flow<List<PhysicalBookEntity>>

	@Query("SELECT * FROM physical_books WHERE id = :id")
	fun observe(id: Long): Flow<PhysicalBookEntity?>

	@Query("SELECT * FROM physical_books WHERE id = :id")
	suspend fun get(id: Long): PhysicalBookEntity?

	@Insert
	suspend fun insert(book: PhysicalBookEntity): Long

	@Update
	suspend fun update(book: PhysicalBookEntity)

	@Query("DELETE FROM physical_books WHERE id = :id")
	suspend fun delete(id: Long)
}
