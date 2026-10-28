package com.entsgra.reader.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.database.entity.LogRow
import com.entsgra.reader.core.database.entity.PhysicalBookEntity
import com.entsgra.reader.core.database.entity.ReadingSessionEntity
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.ReadingItem
import com.entsgra.reader.core.model.ReadingStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingLogDaoTest {
	private val context = ApplicationProvider.getApplicationContext<Context>()
	private val name = "log-test.db"
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

	private suspend fun ebook(title: String, folder: Long? = null) = db.bookDao().insert(
		BookEntity(
			folderId = folder,
			title = title,
			author = null,
			format = BookFormatId.EPUB,
			fileName = "file-$title",
			fileSize = 1,
			contentHash = "hash-$title",
			coverFileName = null,
			pageCount = null,
			addedAt = 1,
			lastOpenedAt = null,
		),
	)

	private suspend fun physical(title: String) = db.physicalBookDao().insert(
		PhysicalBookEntity(title = title, author = null, pageCount = 300, isbn = null, status = ReadingStatus.READING, addedAt = 1, updatedAt = 1),
	)

	private suspend fun sitting(item: ReadingItem, start: Long, end: Long?) {
		val id = db.readingSessionDao().insert(ReadingSessionEntity.of(item, startedAt = start, startPage = 1))
		if (end != null) db.readingSessionDao().end(id, end, 5)
	}

	private suspend fun log(visible: List<Long>): List<LogRow> {
		val result = db.readingSessionDao().pageLog(visible).load(
			PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false),
		)
		return (result as PagingSource.LoadResult.Page).data
	}

	@Test
	fun logMergesEbookAndPhysicalSittingsNewestFirst() = runTest {
		val ebook = ebook("Vertigo")
		val shelf = physical("Austerlitz")
		sitting(ReadingItem.Ebook(ebook), start = 100, end = 200)
		sitting(ReadingItem.Physical(shelf), start = 300, end = 400)
		sitting(ReadingItem.Ebook(ebook), start = 500, end = null)

		val rows = log(visible = emptyList())
		assertThat(rows.map { it.title }).containsExactly("Austerlitz", "Vertigo").inOrder()
		assertThat(rows.first().session.physicalBookId).isEqualTo(shelf)
	}

	@Test
	fun ebooksInHiddenFoldersStayOutOfTheLog() = runTest {
		val open = db.folderDao().insert(FolderEntity(parentId = null, name = "Open", createdAt = 1))
		val locked = db.folderDao().insert(FolderEntity(parentId = null, name = "Locked", lock = FolderLock.DEVICE, createdAt = 1))
		sitting(ReadingItem.Ebook(ebook("Seen", open)), start = 1, end = 2)
		sitting(ReadingItem.Ebook(ebook("Hidden", locked)), start = 3, end = 4)
		sitting(ReadingItem.Physical(physical("Shelf")), start = 5, end = 6)

		assertThat(log(visible = listOf(open)).map { it.title }).containsExactly("Shelf", "Seen").inOrder()
	}

	@Test
	fun readingTimeCountsFinishedSittingsSince() = runTest {
		val book = physical("Austerlitz")
		sitting(ReadingItem.Physical(book), start = 1_000, end = 61_000)
		sitting(ReadingItem.Physical(book), start = 100_000, end = 130_000)
		sitting(ReadingItem.Physical(book), start = 200_000, end = null)

		assertThat(db.readingSessionDao().observeReadingMs(since = 0).first()).isEqualTo(90_000)
		assertThat(db.readingSessionDao().observeReadingMs(since = 50_000).first()).isEqualTo(30_000)
		assertThat(db.readingSessionDao().observeReadingMs(since = 500_000).first()).isEqualTo(0)
	}

	@Test
	fun deletingAPhysicalBookTakesItsSittings() = runTest {
		val book = physical("Austerlitz")
		sitting(ReadingItem.Physical(book), start = 1, end = 2)
		assertThat(db.readingSessionDao().observeForPhysicalBook(book).first()).hasSize(1)

		db.physicalBookDao().delete(book)

		assertThat(db.readingSessionDao().observeForPhysicalBook(book).first()).isEmpty()
		assertThat(log(visible = emptyList())).isEmpty()
	}
}
