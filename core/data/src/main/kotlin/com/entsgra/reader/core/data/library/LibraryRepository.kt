package com.entsgra.reader.core.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.ReaderDatabase
import com.entsgra.reader.core.database.dao.CountsRow
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.database.entity.asModel
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderItem
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.FolderNode
import com.entsgra.reader.core.model.LibraryCounts
import com.entsgra.reader.core.model.LibrarySort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Outcome of naming a folder. */
enum class NameCheck { OK, BLANK, TOO_LONG, TAKEN }

/**
 * Folders and books. Every query waits for the encrypted database to open off the main
 * thread; flows start once it has.
 */
@Singleton
class LibraryRepository @Inject constructor(
	private val database: DatabaseProvider,
	private val files: BookFiles,
	private val covers: CoverStore,
) {
	fun folders(parentId: Long?): Flow<List<FolderItem>> = withDb { db ->
		db.folderDao().observeChildrenWithCounts(parentId).map { rows ->
			rows.map { FolderItem(it.folder.asModel(), it.itemCount) }
		}
	}

	fun folder(id: Long): Flow<Folder?> = withDb { db -> db.folderDao().observe(id).map { it?.asModel() } }

	/** Totals for [folderId] and everything below it; the whole library for null. */
	fun counts(folderId: Long?): Flow<LibraryCounts> = withDb { db ->
		val rows = if (folderId == null) {
			db.folderDao().observeLibraryCounts()
		} else {
			db.folderDao().observeSubtreeCounts(folderId)
		}
		rows.map(CountsRow::asModel)
	}

	fun books(folderId: Long?, sort: LibrarySort): Flow<PagingData<Book>> = withDb { db ->
		pager {
			when (sort) {
				LibrarySort.TITLE -> db.bookDao().pageByTitle(folderId)
				LibrarySort.RECENT -> db.bookDao().pageByOpened(folderId)
				LibrarySort.ADDED -> db.bookDao().pageByAdded(folderId)
			}
		}
	}

	/** Books matching [query] by title or author, in folders that are not locked. */
	fun search(query: String): Flow<PagingData<Book>> = withDb { db ->
		val match = ftsMatch(query)
		if (match == null) {
			flow { emit(PagingData.empty()) }
		} else {
			val visible = visibleFolderIds(db.folderDao().all())
			pager { db.bookDao().search(match, visible) }
		}
	}

	fun book(id: Long): Flow<Book?> = withDb { db -> db.bookDao().observe(id).map { it?.asModel() } }

	/** Every folder, depth first and sorted by name, for picking a destination. */
	fun folderTree(): Flow<List<FolderNode>> = withDb { db ->
		db.folderDao().observeAll().map { entities -> flattenTree(entities.map(FolderEntity::asModel)) }
	}

	suspend fun createFolder(parentId: Long?, name: String): NameCheck {
		val db = database.get()
		val clean = name.trim()
		val check = checkName(db, parentId, clean, exceptId = 0)
		if (check == NameCheck.OK) {
			db.folderDao().insert(
				FolderEntity(parentId = parentId, name = clean, createdAt = System.currentTimeMillis()),
			)
		}
		return check
	}

	suspend fun renameFolder(id: Long, name: String): NameCheck {
		val db = database.get()
		val folder = db.folderDao().get(id) ?: return NameCheck.OK
		val clean = name.trim()
		val check = checkName(db, folder.parentId, clean, exceptId = id)
		if (check == NameCheck.OK) db.folderDao().rename(id, clean)
		return check
	}

	/** False if [parentId] is the folder itself or inside it. */
	suspend fun moveFolder(id: Long, parentId: Long?): Boolean =
		database.get().libraryDao().moveFolder(id, parentId)

	/** Deletes the folder, its subfolders and every book in them, files included. */
	suspend fun deleteFolder(id: Long) {
		val removed = database.get().libraryDao().deleteFolderTree(id)
		withContext(Dispatchers.IO) { removed.forEach(files::delete) }
		if (removed.any { it.coverFileName != null }) covers.clearMemory()
	}

	suspend fun moveBook(id: Long, folderId: Long?) = database.get().bookDao().move(id, folderId)

	suspend fun updateBook(id: Long, title: String, author: String?) {
		val clean = title.trim()
		if (clean.isEmpty()) return
		database.get().bookDao().updateDetails(id, clean, author?.trim()?.takeIf { it.isNotEmpty() })
	}

	suspend fun deleteBook(id: Long) {
		val removed = database.get().libraryDao().deleteBook(id) ?: return
		withContext(Dispatchers.IO) { files.delete(removed) }
	}

	private suspend fun checkName(db: ReaderDatabase, parentId: Long?, name: String, exceptId: Long) = when {
		name.isEmpty() -> NameCheck.BLANK
		name.length > MAX_NAME -> NameCheck.TOO_LONG
		db.folderDao().countSiblingsNamed(parentId, name, exceptId) > 0 -> NameCheck.TAKEN
		else -> NameCheck.OK
	}

	private fun pager(source: () -> androidx.paging.PagingSource<Int, BookEntity>): Flow<PagingData<Book>> =
		Pager(
			// Placeholders keep the grid's scrollbar and item positions stable while pages load.
			config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true),
			pagingSourceFactory = source,
		).flow.map { page -> page.map(BookEntity::asModel) }

	private fun <T> withDb(block: suspend (ReaderDatabase) -> Flow<T>): Flow<T> =
		flow { emitAll(block(database.get())) }

	companion object {
		const val MAX_NAME = 80
		private const val PAGE_SIZE = 60
	}
}

private fun CountsRow.asModel() = LibraryCounts(books = books, folders = folders)

/**
 * Folders whose content may show: unlocked, with no locked ancestor. Search passes these to
 * the query so a locked folder's books never appear in results.
 */
internal fun visibleFolderIds(folders: List<FolderEntity>): List<Long> {
	val byId = folders.associateBy { it.id }
	val memo = HashMap<Long, Boolean>()
	fun visible(id: Long, depth: Int): Boolean = memo.getOrPut(id) {
		val folder = byId[id] ?: return@getOrPut false
		// A corrupt cycle would recurse forever; treat it as hidden.
		if (depth > folders.size) return@getOrPut false
		val parentId = folder.parentId
		folder.lock == FolderLock.NONE && (parentId == null || visible(parentId, depth + 1))
	}
	return folders.map { it.id }.filter { visible(it, 0) }
}

/** Depth-first, siblings by name ignoring case. */
internal fun flattenTree(folders: List<Folder>): List<FolderNode> {
	val children = folders.groupBy { it.parentId }
	val out = ArrayList<FolderNode>(folders.size)
	fun walk(parentId: Long?, depth: Int) {
		children[parentId].orEmpty()
			.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
			.forEach { folder ->
				out += FolderNode(folder, depth)
				walk(folder.id, depth + 1)
			}
	}
	walk(null, 0)
	return out
}

/**
 * Turns what the person typed into an FTS4 MATCH expression: every word as a prefix, all
 * required. Punctuation and FTS operators are dropped, so input can never break the query.
 * Null when nothing searchable is left.
 */
internal fun ftsMatch(query: String): String? {
	val words = query.lowercase()
		.split(Regex("[^\\p{L}\\p{N}]+"))
		.filter { it.isNotEmpty() }
	return if (words.isEmpty()) null else words.joinToString(" ") { "$it*" }
}
