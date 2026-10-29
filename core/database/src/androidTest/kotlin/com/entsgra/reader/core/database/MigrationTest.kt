package com.entsgra.reader.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
	init {
		System.loadLibrary("sqlcipher")
	}

	@get:Rule
	val helper = MigrationTestHelper(
		InstrumentationRegistry.getInstrumentation(),
		ReaderDatabase::class.java,
		emptyList(),
		SupportOpenHelperFactory(ByteArray(32) { it.toByte() }),
	)

	@Test
	fun v1To2KeepsBooksAndRebuildsTheIndex() {
		helper.createDatabase(NAME, 1).use { db ->
			db.execSQL(
				"INSERT INTO books (folder_id, title, author, format, file_name, file_size, content_hash, " +
					"reading_direction, spread_mode, added_at) " +
					"VALUES (NULL, 'Über die Brücke', NULL, 'PDF', 'f', 1, 'h', 'LTR', 'AUTO', 1)",
			)
		}

		val db = helper.runMigrationsAndValidate(NAME, 2, true, *ALL_MIGRATIONS)

		db.query("SELECT title FROM books_fts WHERE books_fts MATCH 'uber*'").use {
			assertThat(it.moveToFirst()).isTrue()
			assertThat(it.getString(0)).isEqualTo("Über die Brücke")
		}
		// Triggers still feed the new index.
		db.execSQL("UPDATE books SET title = 'Austerlitz'")
		db.query("SELECT COUNT(*) FROM books_fts WHERE books_fts MATCH 'austerlitz'").use {
			it.moveToFirst()
			assertThat(it.getInt(0)).isEqualTo(1)
		}
	}

	@Test
	fun v2To3MarksExistingBooksForMetadata() {
		helper.createDatabase(NAME, 2).use { db ->
			db.execSQL(
				"INSERT INTO books (folder_id, title, author, format, file_name, file_size, content_hash, " +
					"reading_direction, spread_mode, added_at) " +
					"VALUES (NULL, 'Vertigo', NULL, 'CBZ', 'f', 1, 'h', 'LTR', 'AUTO', 1)",
			)
		}

		val db = helper.runMigrationsAndValidate(NAME, 3, true, *ALL_MIGRATIONS)

		db.query("SELECT metadata_version FROM books").use {
			assertThat(it.moveToFirst()).isTrue()
			assertThat(it.getInt(0)).isEqualTo(0)
		}
	}

	@Test
	fun v3To4KeepsSessionsWithoutProgress() {
		helper.createDatabase(NAME, 3).use { db ->
			db.execSQL(
				"INSERT INTO books (id, folder_id, title, author, format, file_name, file_size, content_hash, " +
					"reading_direction, spread_mode, added_at, metadata_version) " +
					"VALUES (1, NULL, 'Vertigo', NULL, 'PDF', 'f', 1, 'h', 'LTR', 'AUTO', 1, 1)",
			)
			db.execSQL(
				"INSERT INTO reading_sessions (book_id, physical_book_id, started_at, ended_at, start_page, end_page) " +
					"VALUES (1, NULL, 10, 20, 3, 9)",
			)
		}

		val db = helper.runMigrationsAndValidate(NAME, 4, true, *ALL_MIGRATIONS)

		db.query("SELECT start_page, end_page, start_progress, end_progress FROM reading_sessions").use {
			assertThat(it.moveToFirst()).isTrue()
			assertThat(it.getInt(0)).isEqualTo(3)
			assertThat(it.getInt(1)).isEqualTo(9)
			assertThat(it.isNull(2)).isTrue()
			assertThat(it.isNull(3)).isTrue()
		}
	}

	@Test
	fun v4To5KeepsBooksPlainAndDropsLocksWithoutKeys() {
		helper.createDatabase(NAME, 4).use { db ->
			db.execSQL("INSERT INTO folders (id, parent_id, name, lock, created_at) VALUES (1, NULL, 'Open', 'NONE', 1)")
			db.execSQL("INSERT INTO folders (id, parent_id, name, lock, created_at) VALUES (2, NULL, 'Old', 'DEVICE', 1)")
			db.execSQL(
				"INSERT INTO books (id, folder_id, title, author, format, file_name, file_size, content_hash, " +
					"reading_direction, spread_mode, added_at, metadata_version) " +
					"VALUES (1, 2, 'Vertigo', 'W. G. Sebald', 'PDF', 'f', 1, 'h', 'LTR', 'AUTO', 1, 1)",
			)
		}

		val db = helper.runMigrationsAndValidate(NAME, 5, true, *ALL_MIGRATIONS)

		db.query("SELECT lock, key_folder_id, sealed_name FROM folders ORDER BY id").use {
			while (it.moveToNext()) {
				assertThat(it.getString(0)).isEqualTo("NONE")
				assertThat(it.isNull(1)).isTrue()
				assertThat(it.isNull(2)).isTrue()
			}
		}
		db.query("SELECT title, author, key_folder_id, sealed FROM books").use {
			assertThat(it.moveToFirst()).isTrue()
			assertThat(it.getString(0)).isEqualTo("Vertigo")
			assertThat(it.getString(1)).isEqualTo("W. G. Sebald")
			assertThat(it.isNull(2)).isTrue()
			assertThat(it.isNull(3)).isTrue()
		}
		// Keys go with their folder.
		db.execSQL(
			"INSERT INTO folder_keys (folder_id, kdf_memory_kib, kdf_iterations, kdf_parallelism, salt, sealed, " +
				"biometric, created_at) VALUES (1, 65536, 3, 1, x'00', x'01', NULL, 1)",
		)
		db.query("SELECT removing FROM folder_keys").use {
			assertThat(it.moveToFirst()).isTrue()
			assertThat(it.getInt(0)).isEqualTo(0)
		}
		db.execSQL("PRAGMA foreign_keys = ON")
		db.execSQL("DELETE FROM folders WHERE id = 1")
		db.query("SELECT COUNT(*) FROM folder_keys").use {
			it.moveToFirst()
			assertThat(it.getInt(0)).isEqualTo(0)
		}
	}

	private companion object {
		const val NAME = "migration-test.db"
	}
}
