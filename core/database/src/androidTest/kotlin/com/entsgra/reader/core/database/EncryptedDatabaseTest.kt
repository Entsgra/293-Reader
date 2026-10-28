package com.entsgra.reader.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.database.entity.ReadingSessionEntity
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.ReadingItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
	private val context = ApplicationProvider.getApplicationContext<Context>()
	private val name = "encrypted-test.db"
	private lateinit var db: ReaderDatabase

	@Before
	fun open() {
		System.loadLibrary("sqlcipher")
		context.deleteDatabase(name)
		db = Room.databaseBuilder(context, ReaderDatabase::class.java, name)
			.openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { it.toByte() }))
			.build()
	}

	@After
	fun close() {
		db.close()
		context.deleteDatabase(name)
	}

	@Test
	fun fileOnDiskIsNotPlainSqlite() = runTest {
		db.folderDao().insert(FolderEntity(parentId = null, name = "Comics", createdAt = 1))
		db.close()

		val header = context.getDatabasePath(name).readBytes().copyOf(16)
		assertThat(String(header, Charsets.US_ASCII)).isNotEqualTo("SQLite format 3\u0000")
	}

	@Test
	fun reportsCipherProvider() {
		// Recorded in THIRD_PARTY_NOTICES.md: the crypto library linked into SQLCipher.
		fun pragma(name: String) = db.openHelper.writableDatabase.query("PRAGMA $name")
			.use { it.moveToFirst(); it.getString(0) }
		val provider = "${pragma("cipher_provider")} ${pragma("cipher_provider_version")}"
		android.util.Log.i("CipherProvider", provider)
		assertThat(provider).isNotEmpty()
	}

	@Test
	fun searchFindsByTitleAndHidesLockedFolders() = runTest {
		val open = db.folderDao().insert(FolderEntity(parentId = null, name = "Open", createdAt = 1))
		val locked = db.folderDao().insert(FolderEntity(parentId = null, name = "Locked", createdAt = 1))
		db.bookDao().insert(book("Austerlitz", folderId = open))
		db.bookDao().insert(book("Austerlitz again", folderId = locked))

		val source = db.bookDao().search("austerlitz*", visibleFolderIds = listOf(open))
		val page = source.load(
			androidx.paging.PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
		) as androidx.paging.PagingSource.LoadResult.Page

		assertThat(page.data.map { it.title }).containsExactly("Austerlitz")
	}

	@Test
	fun sessionNeedsExactlyOneBook() {
		assertThrows(IllegalArgumentException::class.java) {
			ReadingSessionEntity(
				bookId = 1,
				physicalBookId = 1,
				startedAt = 0,
				endedAt = null,
				startPage = 0,
				endPage = null,
			)
		}
		assertThat(ReadingSessionEntity.of(ReadingItem.Ebook(4), startedAt = 0, startPage = 3).bookId)
			.isEqualTo(4)
	}

	private fun book(title: String, folderId: Long?) = BookEntity(
		folderId = folderId,
		title = title,
		author = null,
		format = BookFormatId.EPUB,
		fileName = title.hashCode().toString(),
		fileSize = 1,
		contentHash = title,
		coverFileName = null,
		pageCount = null,
		addedAt = 0,
		lastOpenedAt = null,
	)
}
