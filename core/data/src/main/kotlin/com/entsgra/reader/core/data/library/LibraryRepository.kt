package com.entsgra.reader.core.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.entsgra.reader.core.data.ApplicationScope
import com.entsgra.reader.core.data.lock.BookDetails
import com.entsgra.reader.core.data.lock.MoveCheck
import com.entsgra.reader.core.data.lock.Rekeyer
import com.entsgra.reader.core.data.lock.Sealer
import com.entsgra.reader.core.data.lock.folderMoveCheck
import com.entsgra.reader.core.data.lock.lockRootOf
import com.entsgra.reader.core.data.lock.subtree
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
import com.entsgra.reader.core.security.FolderVault
import com.entsgra.reader.core.security.Keyring
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome of naming a folder. */
enum class NameCheck { OK, BLANK, TOO_LONG, TAKEN, LOCKED }

/**
 * Folders and books. Every query waits for the encrypted database to open off the main
 * thread; flows start once it has. Inside a locked folder, names and details are sealed in the
 * database, so those listings are unsealed and sorted in memory while the folder is unlocked,
 * and come back empty while it is locked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepository @Inject constructor(
	private val database: DatabaseProvider,
	private val files: BookFiles,
	private val covers: CoverStore,
	private val sealer: Sealer,
	private val keyring: Keyring,
	private val vault: FolderVault,
	private val rekeyer: Rekeyer,
	@ApplicationScope private val scope: CoroutineScope,
) {
	fun folders(parentId: Long?): Flow<List<FolderItem>> = withDb { db ->
		combine(db.folderDao().observeChildrenWithCounts(parentId), keyring.unlocked) { rows, unlocked ->
			rows.mapNotNull { row ->
				val name = sealer.folderName(row.folder) ?: return@mapNotNull null
				val closed = row.folder.lock != FolderLock.NONE && row.folder.id !in unlocked
				FolderItem(row.folder.asModel(name), row.itemCount, closed)
			}.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.folder.name })
		}
	}

	/** The folder; its name is empty while it is sealed and its key is locked. */
	fun folder(id: Long): Flow<Folder?> = withDb { db ->
		combine(db.folderDao().observe(id), keyring.unlocked) { entity, _ ->
			entity?.let { it.asModel(sealer.folderName(it).orEmpty()) }
		}
	}

	/**
	 * The locked folder whose key covers [folderId] (itself or an ancestor), or null. Folders
	 * whose key is open are included: the caller compares with [Keyring.unlocked].
	 */
	fun lockRoot(folderId: Long?): Flow<Long?> = withDb { db ->
		db.folderDao().observeAll().map { all -> lockRootOf(folderId, all.associateBy { it.id }) }.distinctUntilChanged()
	}

	/** Totals for [folderId] and everything below it; the whole library for null. */
	fun counts(folderId: Long?): Flow<LibraryCounts> = withDb { db ->
		val rows = if (folderId == null) {
			db.folderDao().observeLibraryCounts()
		} else {
			db.folderDao().observeSubtreeCounts(folderId)
		}
		rows.map(CountsRow::asModel)
	}

	fun books(folderId: Long?, sort: LibrarySort): Flow<PagingData<Book>> = lockRoot(folderId).flatMapLatest { root ->
		if (root == null) {
			withDb { db ->
				pager {
					when (sort) {
						LibrarySort.TITLE -> db.bookDao().pageByTitle(folderId)
						LibrarySort.RECENT -> db.bookDao().pageByOpened(folderId)
						LibrarySort.ADDED -> db.bookDao().pageByAdded(folderId)
					}
				}
			}
		} else {
			withDb { db ->
				combine(db.bookDao().observeIn(folderId), keyring.unlocked) { rows, _ ->
					PagingData.from(sortBooks(rows.mapNotNull(sealer::book), sort))
				}
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

	/** The book, or null once deleted or while the locked folder it is in is closed. */
	fun book(id: Long): Flow<Book?> = withDb { db ->
		combine(db.bookDao().observe(id), keyring.unlocked) { entity, _ -> entity?.let(sealer::book) }
	}

	/**
	 * Every folder whose name can be read, depth first and sorted by name, for picking a
	 * destination. A closed locked folder is listed, marked closed, without what is inside it.
	 */
	fun folderTree(): Flow<List<FolderNode>> = withDb { db ->
		combine(db.folderDao().observeAll(), keyring.unlocked) { entities, unlocked ->
			val closed = entities.filter { it.lock != FolderLock.NONE && it.id !in unlocked }.map { it.id }.toSet()
			val readable = entities.mapNotNull { entity ->
				// Inside a closed folder nothing shows, not even names that happen to be plain.
				if (entity.parentId != null && lockRootOf(entity.parentId, entities.associateBy { it.id }) in closed) {
					return@mapNotNull null
				}
				sealer.folderName(entity)?.let { entity.asModel(it) }
			}
			flattenTree(readable).map { it.copy(closed = it.folder.id in closed) }
		}
	}

	suspend fun createFolder(parentId: Long?, name: String): NameCheck {
		val db = database.get()
		val clean = name.trim()
		val check = checkName(db, parentId, clean, exceptId = 0)
		if (check != NameCheck.OK) return check
		val key = lockRootOf(parentId, db.folderDao().all().associateBy { it.id })
		if (key != null && keyring[key] == null) return NameCheck.LOCKED
		val id = db.folderDao().insert(
			FolderEntity(parentId = parentId, name = if (key == null) clean else "", createdAt = System.currentTimeMillis()),
		)
		if (key != null) {
			val (stored, sealed) = sealer.storeName(id, clean, key)
			db.folderDao().writeName(id, stored, sealed, key)
		}
		return NameCheck.OK
	}

	suspend fun renameFolder(id: Long, name: String): NameCheck {
		val db = database.get()
		val folder = db.folderDao().get(id) ?: return NameCheck.OK
		val clean = name.trim()
		val check = checkName(db, folder.parentId, clean, exceptId = id)
		if (check != NameCheck.OK) return check
		val key = folder.keyFolderId
		if (key == null) {
			if (folder.lock != FolderLock.NONE && keyring[id] == null) return NameCheck.LOCKED
			db.folderDao().rename(id, clean)
		} else {
			if (keyring[key] == null) return NameCheck.LOCKED
			val (stored, sealed) = sealer.storeName(id, clean, key)
			db.folderDao().writeName(id, stored, sealed, key)
		}
		return NameCheck.OK
	}

	/**
	 * Moves a folder under [parentId] (null: the root). Books that land under another key are
	 * re-encrypted in the background, so both keys must be open.
	 */
	suspend fun moveFolder(id: Long, parentId: Long?): MoveCheck {
		val db = database.get()
		val byId = db.folderDao().all().associateBy { it.id }
		val folder = byId[id] ?: return MoveCheck.OK
		val check = folderMoveCheck(id, parentId, byId)
		if (check != MoveCheck.OK) return check
		if (folder.lock == FolderLock.NONE) {
			val from = lockRootOf(folder.parentId, byId)
			val to = lockRootOf(parentId, byId)
			if (from != to && (!open(from) || !open(to))) return MoveCheck.LOCKED
		} else if (!open(id)) {
			return MoveCheck.LOCKED
		}
		if (!db.libraryDao().moveFolder(id, parentId)) return MoveCheck.INTO_ITSELF
		scope.launch { rekeyer.settle() }
		return MoveCheck.OK
	}

	/**
	 * Deletes the folder, its subfolders and every book in them, files included. Refused (false)
	 * for a locked folder that is closed, or one holding such a folder.
	 */
	suspend fun deleteFolder(id: Long): Boolean {
		val db = database.get()
		val all = db.folderDao().all()
		val inside = subtree(id, all)
		val locks = all.filter { it.id in inside && it.lock != FolderLock.NONE }.map { it.id }
		val byId = all.associateBy { it.id }
		val root = lockRootOf(id, byId)
		if ((locks + listOfNotNull(root)).any { !open(it) }) return false
		val removed = db.libraryDao().deleteFolderTree(id)
		withContext(Dispatchers.IO) {
			removed.forEach(files::delete)
			// Their key rows went with the folders; the Keystore wraps go now.
			locks.forEach(vault::forget)
		}
		if (removed.any { it.coverFileName != null }) covers.clearMemory()
		return true
	}

	/** Moves a book; one that lands under another key is re-encrypted in the background. */
	suspend fun moveBook(id: Long, folderId: Long?): MoveCheck {
		val db = database.get()
		val book = db.bookDao().get(id) ?: return MoveCheck.OK
		val to = lockRootOf(folderId, db.folderDao().all().associateBy { it.id })
		if (book.keyFolderId == to) {
			db.bookDao().move(id, folderId)
			return MoveCheck.OK
		}
		if (!open(book.keyFolderId) || !open(to)) return MoveCheck.LOCKED
		scope.launch { rekeyer.rekey(book, to, folderId) }
		return MoveCheck.OK
	}

	suspend fun updateBook(id: Long, title: String, author: String?) {
		val clean = title.trim()
		if (clean.isEmpty()) return
		val cleanAuthor = author?.trim()?.takeIf { it.isNotEmpty() }
		val books = database.get().bookDao()
		val book = books.get(id) ?: return
		if (book.keyFolderId == null) {
			books.updateDetails(id, clean, cleanAuthor)
		} else {
			val current = sealer.details(book) ?: return
			val stored = sealer.store(BookDetails(clean, cleanAuthor, current.contentHash), book.fileName, book.keyFolderId)
			books.writeDetails(id, stored.title, stored.author, stored.contentHash, stored.sealed)
		}
	}

	suspend fun deleteBook(id: Long) {
		val removed = database.get().libraryDao().deleteBook(id) ?: return
		withContext(Dispatchers.IO) { files.delete(removed) }
	}

	private fun open(key: Long?) = key == null || keyring[key] != null

	private suspend fun checkName(db: ReaderDatabase, parentId: Long?, name: String, exceptId: Long): NameCheck {
		if (name.isEmpty()) return NameCheck.BLANK
		if (name.length > MAX_NAME) return NameCheck.TOO_LONG
		// Sealed names cannot be compared in SQL; siblings are few, so compare them here.
		val taken = db.folderDao().children(parentId).any { sibling ->
			sibling.id != exceptId && sealer.folderName(sibling)?.equals(name, ignoreCase = true) == true
		}
		return if (taken) NameCheck.TAKEN else NameCheck.OK
	}

	private fun pager(source: () -> androidx.paging.PagingSource<Int, BookEntity>): Flow<PagingData<Book>> =
		Pager(
			// Placeholders keep the grid's scrollbar and item positions stable while pages load.
			config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true),
			pagingSourceFactory = source,
		).flow.map { page -> page.map { it.asModel() } }

	private fun <T> withDb(block: suspend (ReaderDatabase) -> Flow<T>): Flow<T> =
		flow { emitAll(block(database.get())) }

	companion object {
		const val MAX_NAME = 80
		private const val PAGE_SIZE = 60
	}
}

private fun CountsRow.asModel() = LibraryCounts(books = books, folders = folders)

/** The same orders the SQL listings use, for a locked folder's books unsealed in memory. */
internal fun sortBooks(books: List<Book>, sort: LibrarySort): List<Book> = when (sort) {
	LibrarySort.TITLE -> books.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
	LibrarySort.ADDED -> books.sortedByDescending { it.addedAt }
	LibrarySort.RECENT -> books.sortedWith(
		compareBy<Book> { it.lastOpenedAt == null }
			.thenByDescending { it.lastOpenedAt }
			.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
	)
}

/**
 * Folders whose content may show: not locked, with no locked ancestor. Search passes these to
 * the query so a locked folder's books never appear in results, open or not.
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
