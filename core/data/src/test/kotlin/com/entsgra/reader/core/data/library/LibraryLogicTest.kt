package com.entsgra.reader.core.data.library

import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderLock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryLogicTest {
	@Test
	fun titleFromFileName() {
		assertThat(titleFrom("the_rings_of_saturn.epub")).isEqualTo("the rings of saturn")
		assertThat(titleFrom("Austerlitz  (2001).v2.pdf")).isEqualTo("Austerlitz (2001).v2")
		assertThat(titleFrom(".hidden")).isEqualTo(".hidden")
	}

	@Test
	fun searchWordsBecomeRequiredPrefixes() {
		assertThat(ftsMatch("Rings of")).isEqualTo("rings* of*")
	}

	@Test
	fun searchDropsOperatorsAndQuotes() {
		assertThat(ftsMatch("\"saturn\" OR -NEAR*")).isEqualTo("saturn* or* near*")
		assertThat(ftsMatch("  ** \"\" ")).isNull()
	}

	@Test
	fun searchKeepsNonLatinLetters() {
		assertThat(ftsMatch("Kafka über Prag")).isEqualTo("kafka* über* prag*")
	}

	@Test
	fun lockedFolderHidesItsWholeSubtree() {
		val folders = listOf(
			FolderEntity(id = 1, parentId = null, name = "Open", createdAt = 0),
			FolderEntity(id = 2, parentId = 1, name = "Inside open", createdAt = 0),
			FolderEntity(id = 3, parentId = null, name = "Locked", lock = FolderLock.PIN, createdAt = 0),
			FolderEntity(id = 4, parentId = 3, name = "Inside locked", createdAt = 0),
			FolderEntity(id = 5, parentId = 4, name = "Deeper", createdAt = 0),
		)
		assertThat(visibleFolderIds(folders)).containsExactly(1L, 2L)
	}

	@Test
	fun cycleIsHiddenNotInfinite() {
		val folders = listOf(
			FolderEntity(id = 1, parentId = 2, name = "A", createdAt = 0),
			FolderEntity(id = 2, parentId = 1, name = "B", createdAt = 0),
		)
		assertThat(visibleFolderIds(folders)).isEmpty()
	}

	@Test
	fun treeIsDepthFirstAndSortedByName() {
		fun folder(id: Long, parent: Long?, name: String) = Folder(id, parent, name, FolderLock.NONE, 0)
		val tree = flattenTree(
			listOf(
				folder(1, null, "comics"),
				folder(2, null, "Books"),
				folder(3, 1, "Manga"),
				folder(4, 1, "bande dessinée"),
			),
		)
		assertThat(tree.map { it.folder.name to it.depth }).containsExactly(
			"Books" to 0,
			"comics" to 0,
			"bande dessinée" to 1,
			"Manga" to 1,
		).inOrder()
	}
}
