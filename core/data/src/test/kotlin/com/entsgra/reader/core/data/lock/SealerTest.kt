package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.security.Keyring
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SealerTest {
	private val sealer = Sealer(Keyring())

	private fun book(keyFolderId: Long?, sealed: ByteArray?) = BookEntity(
		id = 1, folderId = 3, title = if (sealed == null) "Austerlitz" else "", author = null,
		format = BookFormatId.EPUB, fileName = "abc", fileSize = 1, contentHash = if (sealed == null) "h" else "",
		coverFileName = null, pageCount = null, addedAt = 0, lastOpenedAt = null,
		keyFolderId = keyFolderId, sealed = sealed,
	)

	@Test
	fun detailsRoundTrip() {
		val details = listOf(
			BookDetails("Die Ringe des Saturn", "W. G. Sebald", "ab12"),
			BookDetails("無題", null, ""),
		)
		details.forEach { assertThat(Sealer.decode(Sealer.encode(it))).isEqualTo(it) }
	}

	@Test
	fun plainBooksAndNamesReadAsTheyAre() {
		assertThat(sealer.book(book(null, null))?.title).isEqualTo("Austerlitz")
		assertThat(sealer.folderName(FolderEntity(id = 2, parentId = null, name = "Open", createdAt = 0))).isEqualTo("Open")
	}

	@Test
	fun nothingUnderAClosedKeyReads() {
		assertThat(sealer.book(book(keyFolderId = 3, sealed = ByteArray(40)))).isNull()
		// Even a row the crash left with plain columns stays hidden while its key is closed.
		assertThat(sealer.book(book(keyFolderId = 3, sealed = null))).isNull()
		val sealedName = FolderEntity(id = 2, parentId = 3, name = "", createdAt = 0, keyFolderId = 3, sealedName = ByteArray(30))
		assertThat(sealer.folderName(sealedName)).isNull()
	}
}
