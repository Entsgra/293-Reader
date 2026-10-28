package com.entsgra.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.entsgra.reader.core.database.dao.BookDao
import com.entsgra.reader.core.database.dao.BookmarkDao
import com.entsgra.reader.core.database.dao.FolderDao
import com.entsgra.reader.core.database.dao.LibraryDao
import com.entsgra.reader.core.database.dao.PhysicalBookDao
import com.entsgra.reader.core.database.dao.ReadingPositionDao
import com.entsgra.reader.core.database.dao.ReadingSessionDao
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.BookFtsEntity
import com.entsgra.reader.core.database.entity.BookmarkEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.database.entity.PhysicalBookEntity
import com.entsgra.reader.core.database.entity.ReadingPositionEntity
import com.entsgra.reader.core.database.entity.ReadingSessionEntity

@Database(
	entities = [
		FolderEntity::class,
		BookEntity::class,
		BookFtsEntity::class,
		PhysicalBookEntity::class,
		ReadingSessionEntity::class,
		BookmarkEntity::class,
		ReadingPositionEntity::class,
	],
	version = 2,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ReaderDatabase : RoomDatabase() {
	abstract fun folderDao(): FolderDao
	abstract fun bookDao(): BookDao
	abstract fun libraryDao(): LibraryDao
	abstract fun physicalBookDao(): PhysicalBookDao
	abstract fun readingSessionDao(): ReadingSessionDao
	abstract fun bookmarkDao(): BookmarkDao
	abstract fun readingPositionDao(): ReadingPositionDao
}
