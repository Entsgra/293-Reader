package com.entsgra.reader.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.performScrollToKey
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.entsgra.reader.core.data.library.ImportState
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderItem
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.LibraryCounts
import com.entsgra.reader.core.model.LibrarySort
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.SpreadMode
import com.entsgra.reader.core.screenshots.SCREENSHOT_QUALIFIERS
import com.entsgra.reader.core.screenshots.SCREENSHOT_SDK
import com.entsgra.reader.core.screenshots.captureScreen
import com.entsgra.reader.core.screenshots.screenshotRule
import com.entsgra.reader.core.screenshots.setScreen
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The README's library. No book has a cover file, so every tile shows the generated face:
 * title and format.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_QUALIFIERS)
class LibraryScreenshotTest {
	@get:Rule
	val compose = screenshotRule()

	/** Most recently opened first. */
	private val books = listOf(
		book(1, "Das Kapital", "Karl Marx", BookFormatId.EPUB),
		book(2, "The Holy Bible: New Revised Standard Version", null, BookFormatId.PDF),
		book(3, "An Introduction to Buddhism: Teachings, History and Practices", "Peter Harvey", BookFormatId.PDF),
		book(4, "The Wealth of Nations", "Adam Smith", BookFormatId.EPUB),
		book(5, "Meditations", "Marcus Aurelius", BookFormatId.EPUB),
		book(6, "Tao Te Ching", "Laozi", BookFormatId.TXT),
		book(7, "The Dhammapada", null, BookFormatId.TXT),
		book(8, "The Republic", "Plato", BookFormatId.EPUB),
		book(9, "The Communist Manifesto", "Karl Marx and Friedrich Engels", BookFormatId.PDF),
		book(10, "Thus Spoke Zarathustra", "Friedrich Nietzsche", BookFormatId.EPUB),
		book(11, "Bhagavad Gita", null, BookFormatId.TXT),
		book(12, "The Prince", "Niccolò Machiavelli", BookFormatId.PDF),
	)

	private val folders = listOf(
		FolderItem(Folder(1, null, "Philosophy", FolderLock.NONE, 0), itemCount = 4),
		FolderItem(Folder(2, null, "Religion", FolderLock.NONE, 0), itemCount = 3),
	)

	@Test
	fun library() {
		compose.setScreen { Library() }
		// Past the key card and buttons, so the folders and the covers fill the screen.
		compose.onNode(hasScrollToKeyAction()).performScrollToKey("folders-head")
		compose.captureScreen("library")
	}

	@Composable
	private fun Library() {
		LibraryGrid(
			title = null,
			isRoot = true,
			counts = LibraryCounts(books = books.size + folders.sumOf { it.itemCount }, folders = folders.size),
			folders = folders,
			books = flowOf(PagingData.from(books)).collectAsLazyPagingItems(),
			results = flowOf(PagingData.empty<Book>()).collectAsLazyPagingItems(),
			sort = LibrarySort.RECENT,
			query = "",
			importState = ImportState.Idle,
			rekeying = null,
			cover = { _, _ -> null },
			onBack = {},
			onImport = {},
			onNewFolder = {},
			onDismissImport = {},
			onQuery = {},
			onSort = {},
			onOpenFolder = {},
			onFolderActions = {},
			onOpenBook = {},
			onBookActions = {},
		)
	}

	private fun book(id: Long, title: String, author: String?, format: BookFormatId) = Book(
		id = id,
		folderId = null,
		title = title,
		author = author,
		format = format,
		fileName = "book-$id",
		fileSize = 0,
		contentHash = "",
		coverFileName = null,
		pageCount = null,
		readingDirection = ReadingDirection.LTR,
		spreadMode = SpreadMode.AUTO,
		addedAt = 0,
		lastOpenedAt = null,
	)
}
