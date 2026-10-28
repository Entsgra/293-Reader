package com.entsgra.reader.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.model.BookFormatId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
	private val context = ApplicationProvider.getApplicationContext<Context>()
	private val name = "library-test.db"
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

	private suspend fun folder(name: String, parent: Long? = null) =
		db.folderDao().insert(FolderEntity(parentId = parent, name = name, createdAt = 1))

	private suspend fun book(title: String, folder: Long?, author: String? = null) = db.bookDao().insert(
		BookEntity(
			folderId = folder,
			title = title,
			author = author,
			format = BookFormatId.PDF,
			fileName = "file-$title",
			fileSize = 1,
			contentHash = "hash-$title",
			coverFileName = "cover-$title",
			pageCount = null,
			addedAt = 1,
			lastOpenedAt = null,
		),
	)

	private suspend fun searchTitles(match: String, visible: List<Long>): List<String> {
		val page = db.bookDao().search(match, visible).load(
			PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
		) as PagingSource.LoadResult.Page
		return page.data.map { it.title }
	}

	@Test
	fun countsCoverTheWholeSubtree() = runTest {
		val comics = folder("Comics")
		val manga = folder("Manga", comics)
		book("Root book", null)
		book("Comic", comics)
		book("Manga 1", manga)
		book("Manga 2", manga)

		val sub = db.folderDao().observeSubtreeCounts(comics).first()
		assertThat(sub.books to sub.folders).isEqualTo(3 to 1)
		val all = db.folderDao().observeLibraryCounts().first()
		assertThat(all.books to all.folders).isEqualTo(4 to 2)

		val rows = db.folderDao().observeChildrenWithCounts(comics).first()
		assertThat(rows.map { it.folder.name to it.itemCount }).containsExactly("Manga" to 2)
	}

	@Test
	fun deletingAFolderTakesSubfoldersAndBooksAndReportsTheirFiles() = runTest {
		val comics = folder("Comics")
		val manga = folder("Manga", comics)
		val keep = folder("Keep")
		book("Comic", comics)
		book("Manga 1", manga)
		book("Kept", keep)

		val removed = db.libraryDao().deleteFolderTree(comics)

		assertThat(removed.map { it.fileName }).containsExactly("file-Comic", "file-Manga 1")
		assertThat(db.folderDao().all().map { it.name }).containsExactly("Keep")
		assertThat(db.bookDao().allFileNames()).containsExactly("file-Kept", "cover-Kept")
	}

	@Test
	fun aFolderWithBooksCannotBeDeletedDirectly() = runTest {
		val comics = folder("Comics")
		book("Comic", comics)
		// The RESTRICT key: rows must go through deleteFolderTree so files are cleaned up.
		assertThrows(SQLiteConstraintException::class.java) {
			db.openHelper.writableDatabase.execSQL("DELETE FROM folders WHERE id = $comics")
		}
	}

	@Test
	fun aFolderCannotMoveIntoItself() = runTest {
		val a = folder("A")
		val b = folder("B", a)
		val c = folder("C", b)

		assertThat(db.libraryDao().moveFolder(a, c)).isFalse()
		assertThat(db.libraryDao().moveFolder(a, a)).isFalse()
		assertThat(db.libraryDao().moveFolder(c, null)).isTrue()
		assertThat(db.folderDao().get(c)?.parentId).isNull()
	}

	@Test
	fun siblingNamesAreUniqueIgnoringCase() = runTest {
		val comics = folder("Comics")
		assertThat(db.folderDao().countSiblingsNamed(null, "COMICS")).isEqualTo(1)
		assertThat(db.folderDao().countSiblingsNamed(null, "COMICS", exceptId = comics)).isEqualTo(0)
		assertThat(db.folderDao().countSiblingsNamed(comics, "Comics")).isEqualTo(0)
	}

	@Test
	fun searchFoldsCaseAndDiacriticsAndFollowsRenames() = runTest {
		val id = book("Über die Brücke", null, author = "Émile Zola")
		assertThat(searchTitles("uber*", emptyList())).containsExactly("Über die Brücke")
		assertThat(searchTitles("emile*", emptyList())).containsExactly("Über die Brücke")

		db.bookDao().updateDetails(id, "Austerlitz", author = null)
		assertThat(searchTitles("uber*", emptyList())).isEmpty()
		assertThat(searchTitles("aust*", emptyList())).containsExactly("Austerlitz")
	}
}
