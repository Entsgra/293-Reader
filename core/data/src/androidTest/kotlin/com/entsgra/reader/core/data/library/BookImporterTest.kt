package com.entsgra.reader.core.data.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.entsgra.reader.core.data.TestGraph
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookFormat
import com.entsgra.reader.reader.api.BookFormatRegistry
import com.entsgra.reader.reader.api.BookMetadata
import com.entsgra.reader.reader.api.BookSource
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.PagedDocument
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
	private val graph = TestGraph(context)
	private val scope = graph.scope
	private val cipher = graph.cipher
	private val database = graph.database
	private val covers = graph.covers
	private val importer = graph.importer()
	private val library = graph.library
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

	@Test
	fun importReadsMetadataAndCoverFromTheFormat() = runBlocking<Unit> {
		val format = FakePdfFormat()
		val reading = graph.importer(BookFormatRegistry(setOf(format)))
		reading.import(listOf(file("austerlitz.pdf", pdf)), null)
		withTimeout(30_000) { reading.state.first { it is ImportState.Finished } }

		val book = database.get().bookDao().findByHash(sha256(pdf))!!
		assertThat(book.title).isEqualTo("Austerlitz")
		assertThat(book.author).isEqualTo("W. G. Sebald")
		assertThat(book.pageCount).isEqualTo(7)
		assertThat(book.readingDirection).isEqualTo(ReadingDirection.RTL)
		assertThat(book.metadataVersion).isEqualTo(BookImporter.METADATA_VERSION)
		assertThat(covers.load(book.coverFileName!!, minWidth = 10)).isNotNull()
	}

	@Test
	fun backfillAddsCoversButKeepsEditedDetails() = runBlocking<Unit> {
		runImport(listOf(file("vertigo.pdf", pdf)))
		val books = database.get().bookDao()
		val id = books.findByHash(sha256(pdf))!!.id
		books.updateDetails(id, "My Vertigo", null)

		val format = FakePdfFormat()
		val reading = graph.importer(BookFormatRegistry(setOf(format)))
		reading.backfillMetadata()
		reading.backfillMetadata()

		val book = books.get(id)!!
		assertThat(book.title).isEqualTo("My Vertigo")
		assertThat(book.author).isEqualTo("W. G. Sebald")
		assertThat(book.pageCount).isEqualTo(7)
		assertThat(book.coverFileName).isNotNull()
		// The second pass found nothing left to do.
		assertThat(format.opened).isEqualTo(1)
	}

	/** Stands in for the real PDF reader, which lives in another module. */
	private class FakePdfFormat : BookFormat {
		var opened = 0

		override val id = BookFormatId.PDF

		override fun canOpen(header: ByteArray, fileName: String) = true

		override suspend fun open(source: BookSource): BookDocument {
			opened++
			// Reading the plaintext proves the source decrypts.
			check(source.openStream().use { it.readBytes() }.isNotEmpty())
			return object : PagedDocument {
				override val metadata = BookMetadata("Austerlitz", "W. G. Sebald", ReadingDirection.RTL)
				override val pageCount = 7
				override suspend fun pageSize(index: Int) = PageSize(2, 3)
				override suspend fun render(index: Int, target: Bitmap) = Unit
				override suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap =
					Bitmap.createBitmap(36, 54, Bitmap.Config.ARGB_8888)
				override fun close() = Unit
			}
		}
	}

	private fun sha256(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
		.digest(bytes).joinToString("") { "%02x".format(it) }
}
