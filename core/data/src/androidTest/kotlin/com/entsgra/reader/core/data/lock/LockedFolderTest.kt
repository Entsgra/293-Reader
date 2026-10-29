package com.entsgra.reader.core.data.lock

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.data.SoftBiometricKeys
import com.entsgra.reader.core.data.TestGraph
import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.SecretKind
import com.entsgra.reader.core.security.FolderLockedException
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locked folders end to end on a device: real Keystore, Argon2id at full cost, Tink, SQLCipher.
 * Biometrics use [SoftBiometricKeys], since the emulator cannot pass a prompt from a test.
 */
@RunWith(AndroidJUnit4::class)
class LockedFolderTest {
	private val context = ApplicationProvider.getApplicationContext<Context>()
	private val biometric = SoftBiometricKeys()
	private val graph = TestGraph(context, biometric)
	private val locks = graph.folderLocks
	private val library = graph.library
	private val pdf = Random(293).nextBytes(300_000)

	/** Thrown from a checkpoint: ends the work there, as a crash would, without failing the scope. */
	private class SimulatedCrash : CancellationException("simulated crash")

	@Before
	fun clean() = runBlocking { graph.reset() }

	@After
	fun close() {
		graph.rekeyer.checkpoint = {}
		graph.scope.cancel()
		runBlocking { graph.database.get().close() }
	}

	@Test
	fun lockingSealsDetailsNamesFilesAndCovers() = runBlocking {
		val folder = folder("Private", parent = null)
		val sub = folder("Secret plans", parent = folder)
		val id = book(folder, "Austerlitz")
		val before = db().bookDao().get(id)!!

		lockAndSettle(folder)

		val row = db().bookDao().get(id)!!
		assertThat(row.keyFolderId).isEqualTo(folder)
		assertThat(row.title).isEmpty()
		assertThat(row.author).isNull()
		assertThat(row.contentHash).isEmpty()
		assertThat(row.sealed).isNotNull()
		assertThat(row.fileName).isNotEqualTo(before.fileName)
		assertThat(row.coverFileName).isNotEqualTo(before.coverFileName)
		// What a dump of the database shows: no title, author, hash or subfolder name anywhere.
		assertThat(count("SELECT COUNT(*) FROM books WHERE title LIKE '%Austerlitz%' OR author LIKE '%Sebald%'")).isEqualTo(0)
		assertThat(count("SELECT COUNT(*) FROM books WHERE content_hash LIKE 'hash-%'")).isEqualTo(0)
		assertThat(count("SELECT COUNT(*) FROM books_fts WHERE books_fts MATCH 'austerlitz'")).isEqualTo(0)
		assertThat(count("SELECT COUNT(*) FROM folders WHERE name = 'Secret plans'")).isEqualTo(0)
		assertThat(db().folderDao().get(sub)!!.sealedName).isNotNull()
		// The old app-key copies are gone; the new ones only open with the folder's key.
		assertThat(bookFile(before.fileName).exists()).isFalse()
		assertThat(coverFile(before.coverFileName!!).exists()).isFalse()
		assertThat(readBook(row)).isEqualTo(pdf)
		assertThrows(Exception::class.java) { graph.cipher.openInputStream(bookFile(row.fileName)).use { it.readBytes() } }
		assertThrows(Exception::class.java) { graph.cipher.openInputStream(coverFile(row.coverFileName!!)).use { it.readBytes() } }

		// Unlocked: the library reads it all back.
		assertThat(library.book(id).first()!!.title).isEqualTo("Austerlitz")
		assertThat(library.book(id).first()!!.author).isEqualTo("W. G. Sebald")
		assertThat(library.folders(folder).first().single().folder.name).isEqualTo("Secret plans")

		locks.lockAll()

		assertThat(library.book(id).first()).isNull()
		assertThat(library.folders(folder).first()).isEmpty()
		assertThrows(FolderLockedException::class.java) { graph.cipher.openInputStream(bookFile(row.fileName), folder) }
		assertThat(graph.covers.load(row.coverFileName!!, 10, folder)).isNull()
	}

	@Test
	fun movingInAndOutReencryptsUnderTheDestinationKey() = runBlocking {
		val folder = folder("Private", parent = null)
		lockAndSettle(folder)
		val id = book(parent = null, title = "Vertigo")

		assertThat(library.moveBook(id, folder)).isEqualTo(MoveCheck.OK)
		val inside = eventually { db().bookDao().get(id)!!.takeIf { it.keyFolderId == folder } }
		assertThat(inside.folderId).isEqualTo(folder)
		assertThat(inside.title).isEmpty()
		assertThat(readBook(inside)).isEqualTo(pdf)
		assertThat(bookDir().listFiles()!!.map { it.name }).containsExactly(inside.fileName)

		assertThat(library.moveBook(id, null)).isEqualTo(MoveCheck.OK)
		val outside = eventually { db().bookDao().get(id)!!.takeIf { it.keyFolderId == null } }
		assertThat(outside.folderId).isNull()
		assertThat(outside.title).isEqualTo("Vertigo")
		assertThat(graph.cipher.openInputStream(bookFile(outside.fileName)).use { it.readBytes() }).isEqualTo(pdf)

		// Nothing goes into a folder whose key is closed.
		locks.lockAll()
		assertThat(library.moveBook(id, folder)).isEqualTo(MoveCheck.LOCKED)
		assertThat(db().bookDao().get(id)!!.folderId).isNull()
	}

	@Test
	fun movingAFolderInSealsItsNameAndBooks() = runBlocking {
		val locked = folder("Private", parent = null)
		lockAndSettle(locked)
		val plain = folder("Essays", parent = null)
		val id = book(plain, "Campo Santo")

		assertThat(library.moveFolder(plain, locked)).isEqualTo(MoveCheck.OK)
		val row = eventually { db().bookDao().get(id)!!.takeIf { it.keyFolderId == locked } }
		assertThat(row.title).isEmpty()
		assertThat(db().folderDao().get(plain)!!.name).isEmpty()
		assertThat(library.folders(locked).first().single().folder.name).isEqualTo("Essays")

		// A locked folder cannot go inside another.
		val other = folder("Other", parent = null)
		lockAndSettle(other)
		assertThat(library.moveFolder(other, locked)).isEqualTo(MoveCheck.NESTED_LOCK)
	}

	@Test
	fun crashBeforeTheRowUpdateKeepsTheOldCopy() = runBlocking {
		val folder = folder("Private", parent = null)
		lockAndSettle(folder)
		val id = book(parent = null, title = "Vertigo")
		val before = db().bookDao().get(id)!!
		graph.rekeyer.checkpoint = { if (it == Rekeyer.Stage.NEW_FILES_WRITTEN) throw SimulatedCrash() }

		assertThrows(SimulatedCrash::class.java) { runBlocking { graph.rekeyer.rekey(before, folder, folder) } }
		graph.rekeyer.checkpoint = {}
		assertThat(bookDir().listFiles()!!).hasLength(2)
		graph.importer().sweepOrphans()

		val after = db().bookDao().get(id)!!
		assertThat(after.fileName).isEqualTo(before.fileName)
		assertThat(after.keyFolderId).isNull()
		assertThat(after.folderId).isNull()
		assertThat(bookDir().listFiles()!!.map { it.name }).containsExactly(before.fileName)
		assertThat(coverDir().listFiles()!!.map { it.name }).containsExactly(before.coverFileName)
		assertThat(graph.cipher.openInputStream(bookFile(after.fileName)).use { it.readBytes() }).isEqualTo(pdf)
	}

	@Test
	fun crashAfterTheRowUpdateKeepsTheNewCopy() = runBlocking {
		val folder = folder("Private", parent = null)
		lockAndSettle(folder)
		val id = book(parent = null, title = "Vertigo")
		val before = db().bookDao().get(id)!!
		graph.rekeyer.checkpoint = { if (it == Rekeyer.Stage.ROW_UPDATED) throw SimulatedCrash() }

		assertThrows(SimulatedCrash::class.java) { runBlocking { graph.rekeyer.rekey(before, folder, folder) } }
		graph.rekeyer.checkpoint = {}
		graph.importer().sweepOrphans()

		val after = db().bookDao().get(id)!!
		assertThat(after.keyFolderId).isEqualTo(folder)
		assertThat(after.folderId).isEqualTo(folder)
		assertThat(bookDir().listFiles()!!.map { it.name }).containsExactly(after.fileName)
		assertThat(coverDir().listFiles()!!.map { it.name }).containsExactly(after.coverFileName)
		assertThat(readBook(after)).isEqualTo(pdf)
		assertThat(library.book(id).first()!!.title).isEqualTo("Vertigo")
	}

	@Test
	fun lockingThatCrashedHalfwayFinishesAtTheNextUnlock() = runBlocking {
		val folder = folder("Private", parent = null)
		val first = book(folder, "Austerlitz")
		val second = book(folder, "Vertigo")
		var written = 0
		var crashed = false
		graph.rekeyer.checkpoint = {
			if (it == Rekeyer.Stage.NEW_FILES_WRITTEN && ++written == 2) {
				crashed = true
				throw SimulatedCrash()
			}
		}

		assertThat(locks.lock(folder, SecretKind.PIN, PIN.toCharArray())).isEqualTo(LockCheck.OK)
		eventually { crashed.takeIf { it && graph.rekeyer.progress.value == null } }
		graph.rekeyer.checkpoint = {}
		// The process dies: keys gone, the orphan swept at the next start.
		locks.lockAll()
		graph.importer().sweepOrphans()
		val keys = listOf(first, second).map { db().bookDao().get(it)!!.keyFolderId }
		assertThat(keys).containsExactly(folder, null)
		assertThat(bookDir().listFiles()!!).hasLength(2)

		assertThat(locks.unlock(folder, PIN.toCharArray())).isEqualTo(UnlockResult.Ok)

		eventually { listOf(first, second).all { db().bookDao().get(it)!!.keyFolderId == folder }.takeIf { it } }
		listOf(first, second).forEach { assertThat(readBook(db().bookDao().get(it)!!)).isEqualTo(pdf) }
		assertThat(bookDir().listFiles()!!).hasLength(2)
	}

	@Test
	fun removingTheLockPutsEverythingBackUnderTheAppKey() = runBlocking {
		val folder = folder("Private", parent = null)
		val sub = folder("Secret plans", parent = folder)
		val id = book(folder, "Austerlitz")
		lockAndSettle(folder)
		locks.lockAll()

		assertThat(locks.removeLock(folder, "000000".toCharArray())).isInstanceOf(UnlockResult.Wrong::class.java)
		assertThat(locks.removeLock(folder, PIN.toCharArray())).isEqualTo(UnlockResult.Ok)

		eventually { db().folderKeyDao().get(folder).let { if (it == null) true else null } }
		val row = db().bookDao().get(id)!!
		assertThat(row.keyFolderId).isNull()
		assertThat(row.sealed).isNull()
		assertThat(row.title).isEqualTo("Austerlitz")
		assertThat(row.contentHash).isEqualTo("hash-Austerlitz")
		assertThat(db().folderDao().get(sub)!!.name).isEqualTo("Secret plans")
		assertThat(db().folderDao().get(folder)!!.lock).isEqualTo(FolderLock.NONE)
		assertThat(graph.keyring.unlocked.value).doesNotContain(folder)
		assertThat(graph.cipher.openInputStream(bookFile(row.fileName)).use { it.readBytes() }).isEqualTo(pdf)
		assertThat(bookDir().listFiles()!!).hasLength(1)
	}

	@Test
	fun changingTheSecretKeepsTheFilesAsTheyAre() = runBlocking {
		val folder = folder("Private", parent = null)
		val id = book(folder, "Austerlitz")
		lockAndSettle(folder)
		val before = db().bookDao().get(id)!!

		assertThat(locks.changeSecret(folder, PIN.toCharArray(), SecretKind.PASSPHRASE, PASSPHRASE.toCharArray()))
			.isEqualTo(UnlockResult.Ok)
		locks.lockAll()

		assertThat(db().folderDao().get(folder)!!.lock).isEqualTo(FolderLock.PASSPHRASE)
		assertThat(locks.unlock(folder, PIN.toCharArray())).isInstanceOf(UnlockResult.Wrong::class.java)
		assertThat(locks.unlock(folder, PASSPHRASE.toCharArray())).isEqualTo(UnlockResult.Ok)
		val after = db().bookDao().get(id)!!
		assertThat(after.fileName).isEqualTo(before.fileName)
		assertThat(after.sealed).isEqualTo(before.sealed)
		assertThat(readBook(after)).isEqualTo(pdf)
	}

	@Test
	fun invalidatedBiometricsFallBackToThePinAndCanBeSetUpAgain() = runBlocking {
		val folder = folder("Private", parent = null)
		lockAndSettle(folder)
		locks.biometricEnroll(folder, locks.biometricEnrollCipher(folder))
		assertThat(locks.hasBiometric(folder)).isTrue()
		locks.lockAll()

		val ready = locks.biometricStart(folder) as BiometricStart.Ready
		locks.biometricUnlock(folder, ready.cipher)
		assertThat(graph.keyring.unlocked.value).contains(folder)
		locks.lockAll()

		// A new fingerprint was enrolled: Android deletes the key.
		biometric.invalidate()
		assertThat(locks.biometricStart(folder)).isEqualTo(BiometricStart.Invalidated)
		assertThat(locks.hasBiometric(folder)).isFalse()
		assertThat(locks.biometricStart(folder)).isEqualTo(BiometricStart.None)
		assertThat(locks.unlock(folder, PIN.toCharArray())).isEqualTo(UnlockResult.Ok)

		locks.biometricEnroll(folder, locks.biometricEnrollCipher(folder))
		locks.lockAll()
		val again = locks.biometricStart(folder) as BiometricStart.Ready
		locks.biometricUnlock(folder, again.cipher)
		assertThat(graph.keyring.unlocked.value).contains(folder)
	}

	@Test
	fun locksDoNotNest() = runBlocking {
		val outer = folder("Private", parent = null)
		val inner = folder("Inner", parent = outer)
		lockAndSettle(outer)
		assertThat(locks.lock(inner, SecretKind.PIN, PIN.toCharArray())).isEqualTo(LockCheck.INSIDE_LOCKED)
		assertThat(locks.lock(outer, SecretKind.PIN, PIN.toCharArray())).isEqualTo(LockCheck.ALREADY_LOCKED)
	}

	private suspend fun db() = graph.database.get()

	private suspend fun lockAndSettle(folder: Long) {
		assertThat(locks.lock(folder, SecretKind.PIN, PIN.toCharArray())).isEqualTo(LockCheck.OK)
		graph.rekeyer.settle()
	}

	private suspend fun folder(name: String, parent: Long?): Long {
		assertThat(library.createFolder(parent, name)).isEqualTo(com.entsgra.reader.core.data.library.NameCheck.OK)
		return db().folderDao().children(parent).first { graph.sealer.folderName(it) == name }.id
	}

	private suspend fun book(parent: Long?, title: String): Long {
		val file = graph.files.newBookFile()
		graph.cipher.encrypt(pdf.inputStream(), file)
		val cover = graph.covers.save(Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888))
		return db().bookDao().insert(
			BookEntity(
				folderId = parent, title = title, author = "W. G. Sebald", format = BookFormatId.PDF,
				fileName = file.name, fileSize = pdf.size.toLong(), contentHash = "hash-$title",
				coverFileName = cover, pageCount = null, addedAt = 0, lastOpenedAt = null,
			),
		)
	}

	private fun readBook(row: BookEntity) =
		graph.cipher.openInputStream(bookFile(row.fileName), row.keyFolderId).use { it.readBytes() }

	private fun bookFile(name: String) = graph.files.book(name)

	private fun coverFile(name: String) = graph.files.cover(name)

	private fun bookDir(): File = File(context.noBackupFilesDir, "books")

	private fun coverDir(): File = File(context.noBackupFilesDir, "covers")

	private suspend fun count(sql: String): Int =
		db().openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

	private suspend fun <T : Any> eventually(check: suspend () -> T?): T = withTimeout(60_000) {
		var value = check()
		while (value == null) {
			delay(50)
			value = check()
		}
		value
	}

	private companion object {
		const val PIN = "472913"
		const val PASSPHRASE = "a longer passphrase"
	}
}
