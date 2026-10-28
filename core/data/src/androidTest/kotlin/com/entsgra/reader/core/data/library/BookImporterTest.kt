package com.entsgra.reader.core.data.library

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.security.DatabaseKey
import com.entsgra.reader.core.security.FileCipher
import com.entsgra.reader.core.security.KeystoreCryptoPrimitives
import com.entsgra.reader.reader.api.BookFormatRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The whole import path on a device: content resolver, Tink, SQLCipher, real Keystore. */
@RunWith(AndroidJUnit4::class)
class BookImporterTest {
	private val context = ApplicationProvider.getApplicationContext<Context>()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val crypto = KeystoreCryptoPrimitives(context)
	private val cipher = FileCipher(crypto)
	private val database = DatabaseProvider(context, DatabaseKey(context, crypto))
	private val files = BookFiles(context)
	private val covers = CoverStore(files, cipher)
	private val importer = BookImporter(
		context, database, files, cipher, covers, BookFormatRegistry(emptySet()), scope,
	)
	private val library = LibraryRepository(database, files, covers)
	private val inbox = File(context.cacheDir, "inbox")
	private val stored get() = File(context.noBackupFilesDir, "books")

	@Before
	fun clean() {
		runBlocking { database.get().clearAllTables() }
		stored.deleteRecursively()
		inbox.deleteRecursively()
		inbox.mkdirs()
	}

	@After
	fun close() {
		scope.cancel()
		runBlocking { database.get().close() }
	}

	private fun file(name: String, bytes: ByteArray): Uri =
		Uri.fromFile(File(inbox, name).apply { writeBytes(bytes) })

	private fun zip(vararg entries: Pair<String, ByteArray>, storedFirst: Boolean = false): ByteArray {
		val out = java.io.ByteArrayOutputStream()
		ZipOutputStream(out).use { zip ->
			entries.forEachIndexed { i, (name, data) ->
				val entry = ZipEntry(name)
				if (storedFirst && i == 0) {
					entry.method = ZipEntry.STORED
					entry.size = data.size.toLong()
					entry.crc = CRC32().apply { update(data) }.value
				}
				zip.putNextEntry(entry)
				zip.write(data)
				zip.closeEntry()
			}
		}
		return out.toByteArray()
	}

	private val pdf = "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".toByteArray()

	private suspend fun runImport(uris: List<Uri>, folderId: Long? = null): ImportState.Finished {
		importer.import(uris, folderId)
		return withTimeout(30_000) { importer.state.first { it is ImportState.Finished } as ImportState.Finished }
	}

	@Test
	fun importsEveryFormatEncryptedAndReportsWhatItCannot() = runBlocking<Unit> {
		val result = runImport(
			listOf(
				file("Austerlitz.pdf", pdf),
				file("austerlitz copy.pdf", pdf),
				file(
					"Vertigo.epub",
					zip("mimetype" to "application/epub+zip".toByteArray(), "content.opf" to ByteArray(8), storedFirst = true),
				),
				file("Saga_01.cbz", zip("p1.png" to ByteArray(32))),
				file("Akira.cb7", byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C) + ByteArray(64)),
				file("Über die Brücke.txt", "Über die Brücke".toByteArray()),
				file("Watchmen.cbr", "Rar!\u001A\u0007\u0000".toByteArray() + ByteArray(64)),
				file("report.docx", zip("word/document.xml" to ByteArray(4))),
				Uri.fromFile(File(inbox, "missing.pdf")),
			),
		)

		assertThat(result.imported).isEqualTo(5)
		assertThat(result.duplicates).isEqualTo(1)
		assertThat(result.failures.map { it.fileName to it.reason }).containsExactly(
			"Watchmen.cbr" to ImportFailure.Reason.RAR,
			"report.docx" to ImportFailure.Reason.UNSUPPORTED,
			"missing.pdf" to ImportFailure.Reason.UNREADABLE,
		)

		val books = database.get().bookDao()
		val pdfBook = books.findByHash(sha256(pdf))!!
		assertThat(pdfBook.title).isEqualTo("Austerlitz")
		assertThat(pdfBook.format).isEqualTo(BookFormatId.PDF)
		assertThat(pdfBook.fileSize).isEqualTo(pdf.size.toLong())

		// On disk: one file per imported book, random names, no plaintext.
		val onDisk = stored.listFiles()!!.toList()
		assertThat(onDisk).hasSize(5)
		assertThat(onDisk.map { it.name }).doesNotContain("Austerlitz.pdf")
		val stored = File(stored, pdfBook.fileName).readBytes()
		assertThat(String(stored, Charsets.ISO_8859_1)).doesNotContain("%PDF")
		// And it decrypts back to exactly what was picked.
		assertThat(cipher.openInputStream(File(this@BookImporterTest.stored, pdfBook.fileName)).use { it.readBytes() })
			.isEqualTo(pdf)
	}

	@Test
	fun sweepRemovesFilesWithoutARow() = runBlocking<Unit> {
		runImport(listOf(file("Kept.pdf", pdf)))
		val orphan = File(stored, "0123456789abcdef").apply { writeBytes(ByteArray(4)) }
		val partial = File(stored, "fedcba9876543210.part").apply { writeBytes(ByteArray(4)) }

		importer.sweepOrphans()

		assertThat(orphan.exists()).isFalse()
		assertThat(partial.exists()).isFalse()
		assertThat(stored.listFiles()!!).hasLength(1)
	}

	@Test
	fun deletingAFolderDeletesItsFiles() = runBlocking<Unit> {
		library.createFolder(null, "Comics")
		val comics = database.get().folderDao().all().single().id
		library.createFolder(comics, "Manga")
		val manga = database.get().folderDao().all().single { it.name == "Manga" }.id
		runImport(listOf(file("A.pdf", pdf)), folderId = manga)
		runImport(listOf(file("B.txt", "b".toByteArray())), folderId = null)
		assertThat(stored.listFiles()!!).hasLength(2)

		library.deleteFolder(comics)

		assertThat(database.get().folderDao().all()).isEmpty()
		assertThat(stored.listFiles()!!.map { it.name })
			.containsExactly(database.get().bookDao().allFileNames().single())
	}

	@Test
	fun folderNamesAreChecked() = runBlocking<Unit> {
		assertThat(library.createFolder(null, "  ")).isEqualTo(NameCheck.BLANK)
		assertThat(library.createFolder(null, "x".repeat(81))).isEqualTo(NameCheck.TOO_LONG)
		assertThat(library.createFolder(null, " Comics ")).isEqualTo(NameCheck.OK)
		assertThat(library.createFolder(null, "COMICS")).isEqualTo(NameCheck.TAKEN)
		assertThat(database.get().folderDao().all().single().name).isEqualTo("Comics")
	}

	@Test
	fun searchIgnoresAccentsAndCase() = runBlocking<Unit> {
		runImport(listOf(file("Über die Brücke.txt", "x".toByteArray())))
		val match = ftsMatch("brucke")!!
		val page = database.get().bookDao().search(match, emptyList()).load(
			androidx.paging.PagingSource.LoadParams.Refresh(null, 10, false),
		) as androidx.paging.PagingSource.LoadResult.Page
		assertThat(page.data.map { it.title }).containsExactly("Über die Brücke")
	}

	private fun sha256(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
		.digest(bytes).joinToString("") { "%02x".format(it) }
}
