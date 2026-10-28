package com.entsgra.reader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2: the search index switches to the unicode61 tokenizer. The index holds no data of its
 * own (content=books), so it is dropped, recreated and rebuilt from `books`. The sync
 * triggers on `books` refer to it by name and survive unchanged.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("DROP TABLE IF EXISTS `books_fts`")
		db.execSQL(
			"CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` USING FTS4(" +
				"`title` TEXT NOT NULL, `author` TEXT, tokenize=unicode61, content=`books`)",
		)
		db.execSQL("INSERT INTO `books_fts`(`books_fts`) VALUES ('rebuild')")
	}
}

/** v3: `metadata_version`, so books imported before their format had a reader get covers later. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE `books` ADD COLUMN `metadata_version` INTEGER NOT NULL DEFAULT 0")
	}
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
