package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.data.library.BookFiles
import com.entsgra.reader.core.data.library.StorageLock
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.ReaderDatabase
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.security.FileCipher
import com.entsgra.reader.core.security.FolderLockedException
import com.entsgra.reader.core.security.FolderVault
import com.entsgra.reader.core.security.Keyring
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Books being re-encrypted under another key: [done] of [total]. */
data class Rekeying(val done: Int, val total: Int)

/**
 * Moves books between keys: the app key and locked folders' keys. One book at a time, crash
 * safe:
 * 1. the file and cover are written under the new key, under new random names, and fsynced;
 * 2. one UPDATE points the row at them, with its key and details;
 * 3. the old files are deleted.
 * A crash before step 2 leaves the new files unnamed by any row, after it the old ones; either
 * way the startup sweep deletes what no row names, and exactly one readable copy remains.
 *
 * Every row records the key its files are really under, so a folder half re-encrypted is
 * consistent, and [settle] carries on from whatever rows are still on the wrong key.
 */
@Singleton
class Rekeyer @Inject constructor(
	private val database: DatabaseProvider,
	private val files: BookFiles,
	private val cipher: FileCipher,
	private val sealer: Sealer,
	private val keyring: Keyring,
	private val vault: FolderVault,
	private val storage: StorageLock,
) {
	private val settling = Mutex()
	private val _progress = MutableStateFlow<Rekeying?>(null)
	val progress: StateFlow<Rekeying?> = _progress.asStateFlow()

	/** Test seam: runs between the steps above, to model a crash. */
	internal var checkpoint: (Stage) -> Unit = {}

	internal enum class Stage { NEW_FILES_WRITTEN, ROW_UPDATED }

	/** Re-encrypts one book under [toKey] and, in the same row update, moves it to [toFolder]. */
	suspend fun rekey(book: BookEntity, toKey: Long?, toFolder: Long?) = storage.mutex.withLock {
		withContext(Dispatchers.IO) { rekeyLocked(database.get(), book, toKey, toFolder) }
	}

	/**
	 * Brings every book and folder name under the key its place calls for, as far as the
	 * unlocked keys allow, then finishes removing locks that have nothing left under them.
	 * Safe to call any time; concurrent calls run one after another.
	 */
	suspend fun settle() = settling.withLock {
		val db = database.get()
		val folders = db.folderDao().all()
		val byId = folders.associateBy { it.id }
		val keys = db.folderKeyDao().all()
		val removing = keys.filter { it.removing }.map { it.folderId }.toSet()
		fun available(key: Long?) = key == null || keyring[key] != null

		// Folder names first: cheap, and they are what a locked folder's listing shows.
		for (folder in folders) {
			val wanted = lockRootOf(folder.parentId, byId, removing)
			if (folder.keyFolderId == wanted || !available(folder.keyFolderId) || !available(wanted)) continue
			val name = sealer.folderName(folder) ?: continue
			val (stored, sealed) = sealer.storeName(folder.id, name, wanted)
			db.folderDao().writeName(folder.id, stored, sealed, wanted)
		}

		val pending = db.bookDao().keyPlaces().filter { place ->
			val wanted = lockRootOf(place.folderId, byId, removing)
			place.keyFolderId != wanted && available(place.keyFolderId) && available(wanted)
		}
		if (pending.isNotEmpty()) {
			_progress.value = Rekeying(0, pending.size)
			try {
				pending.forEachIndexed { index, place ->
					storage.mutex.withLock {
						withContext(Dispatchers.IO) {
							val book = db.bookDao().get(place.id) ?: return@withContext
							// Re-read: the book may have moved since the plan was made.
							val now = db.folderDao().all().associateBy { it.id }
							val wanted = lockRootOf(book.folderId, now, removing)
							if (book.keyFolderId != wanted && available(book.keyFolderId) && available(wanted)) {
								try {
									rekeyLocked(db, book, wanted, book.folderId)
								} catch (e: CancellationException) {
									throw e
								} catch (_: Exception) {
									// A damaged file stays where it is; the rest go on.
								}
							}
						}
					}
					_progress.value = Rekeying(index + 1, pending.size)
				}
			} finally {
				_progress.value = null
			}
		}

		for (root in removing) {
			val left = db.bookDao().countUnderKey(root) + db.folderDao().all().count { it.keyFolderId == root }
			if (left == 0) {
				db.folderKeyDao().removeLock(root)
				withContext(Dispatchers.IO) { vault.forget(root) }
			}
		}
		withContext(Dispatchers.IO) { database.checkpoint(db) }
	}

	private suspend fun rekeyLocked(db: ReaderDatabase, book: BookEntity, toKey: Long?, toFolder: Long?) {
		val fromKey = book.keyFolderId
		val details = sealer.details(book) ?: throw FolderLockedException(fromKey!!)
		if (toKey != null) keyring.require(toKey)
		val newBook = files.newBookFile()
		val oldCover = book.coverFileName
		val newCover = oldCover?.let { files.newCoverFile() }
		try {
			copy(files.book(book.fileName), fromKey, newBook, toKey)
			if (oldCover != null && newCover != null) copy(files.cover(oldCover), fromKey, newCover, toKey)
		} catch (e: Throwable) {
			newBook.delete()
			newCover?.delete()
			throw e
		}
		checkpoint(Stage.NEW_FILES_WRITTEN)
		val stored = sealer.store(details, newBook.name, toKey)
		db.bookDao().rekey(
			id = book.id,
			folderId = toFolder,
			fileName = newBook.name,
			coverFileName = newCover?.name,
			keyFolderId = toKey,
			title = stored.title,
			author = stored.author,
			contentHash = stored.contentHash,
			sealed = stored.sealed,
		)
		checkpoint(Stage.ROW_UPDATED)
		files.book(book.fileName).delete()
		book.coverFileName?.let { files.cover(it).delete() }
	}

	private fun copy(from: File, fromKey: Long?, to: File, toKey: Long?) {
		cipher.openInputStream(from, fromKey).use { cipher.encrypt(it, to, toKey) }
	}
}
