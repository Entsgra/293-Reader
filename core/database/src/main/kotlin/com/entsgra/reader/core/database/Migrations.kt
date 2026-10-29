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

/** v4: sittings record progress through the book, since EPUB and TXT have no fixed pages. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE `reading_sessions` ADD COLUMN `start_progress` REAL")
		db.execSQL("ALTER TABLE `reading_sessions` ADD COLUMN `end_progress` REAL")
	}
}

/**
 * v5: locked folders hold their own keys. Books and folder names record which folder's key
 * seals them, and `folder_keys` stores each locked folder's sealed keys. The old lock kinds
 * (device, passphrase) never had a key or a way to be set, so any folder carrying one is
 * reset to unlocked rather than left claiming a lock nothing enforces.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("UPDATE `folders` SET `lock` = 'NONE' WHERE `lock` != 'NONE'")
		db.execSQL("ALTER TABLE `folders` ADD COLUMN `key_folder_id` INTEGER")
		db.execSQL("ALTER TABLE `folders` ADD COLUMN `sealed_name` BLOB")
		db.execSQL("ALTER TABLE `books` ADD COLUMN `key_folder_id` INTEGER")
		db.execSQL("ALTER TABLE `books` ADD COLUMN `sealed` BLOB")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_key_folder_id` ON `books` (`key_folder_id`)")
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS `folder_keys` (`folder_id` INTEGER NOT NULL, " +
				"`kdf_memory_kib` INTEGER NOT NULL, `kdf_iterations` INTEGER NOT NULL, " +
				"`kdf_parallelism` INTEGER NOT NULL, `salt` BLOB NOT NULL, `sealed` BLOB NOT NULL, " +
				"`biometric` BLOB, `removing` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, " +
				"PRIMARY KEY(`folder_id`), FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) " +
				"ON UPDATE NO ACTION ON DELETE CASCADE )",
		)
	}
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
