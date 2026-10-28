package com.entsgra.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.entsgra.reader.core.database.entity.ReadingPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
	@Query("SELECT * FROM reading_positions WHERE book_id = :bookId")
	suspend fun get(bookId: Long): ReadingPositionEntity?

	@Query("SELECT * FROM reading_positions WHERE book_id = :bookId")
	fun observe(bookId: Long): Flow<ReadingPositionEntity?>

	@Upsert
	suspend fun upsert(position: ReadingPositionEntity)
}
