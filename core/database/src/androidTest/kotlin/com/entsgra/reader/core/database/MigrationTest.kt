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

	private companion object {
		const val NAME = "migration-test.db"
	}
}
