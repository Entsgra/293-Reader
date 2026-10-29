package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.LockTimeout
import com.entsgra.reader.core.model.SecretKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockRulesTest {
	// 1 Locked ── 2 ── 3      4 Open ── 5      6 Also locked
	private val folders = listOf(
		FolderEntity(id = 1, parentId = null, name = "Locked", lock = FolderLock.PIN, createdAt = 0),
		FolderEntity(id = 2, parentId = 1, name = "", createdAt = 0, keyFolderId = 1),
		FolderEntity(id = 3, parentId = 2, name = "", createdAt = 0, keyFolderId = 1),
		FolderEntity(id = 4, parentId = null, name = "Open", createdAt = 0),
		FolderEntity(id = 5, parentId = 4, name = "Inside open", createdAt = 0),
		FolderEntity(id = 6, parentId = null, name = "Also locked", lock = FolderLock.PASSPHRASE, createdAt = 0),
	).associateBy { it.id }

	@Test
	fun pinNeedsSixDigitsAndPassphraseEightCharacters() {
		assertThat(checkSecret(SecretKind.PIN, "472913", "472913")).isEqualTo(SecretCheck.OK)
		assertThat(checkSecret(SecretKind.PIN, "47291", "47291")).isEqualTo(SecretCheck.PIN_TOO_SHORT)
		assertThat(checkSecret(SecretKind.PIN, "47291a", "47291a")).isEqualTo(SecretCheck.PIN_NOT_DIGITS)
		assertThat(checkSecret(SecretKind.PASSPHRASE, "seven c", "seven c")).isEqualTo(SecretCheck.PASSPHRASE_TOO_SHORT)
		assertThat(checkSecret(SecretKind.PASSPHRASE, "eight ch", "eight ch")).isEqualTo(SecretCheck.OK)
		// Characters, not UTF-16 units: four emoji are four characters.
		assertThat(checkSecret(SecretKind.PASSPHRASE, "😀😀😀😀", "😀😀😀😀")).isEqualTo(SecretCheck.PASSPHRASE_TOO_SHORT)
		assertThat(checkSecret(SecretKind.PIN, "472913", "472914")).isEqualTo(SecretCheck.MISMATCH)
	}

	@Test
	fun lockRootIsTheNearestLockedFolderUpward() {
		assertThat(lockRootOf(3, folders)).isEqualTo(1)
		assertThat(lockRootOf(1, folders)).isEqualTo(1)
		assertThat(lockRootOf(5, folders)).isNull()
		assertThat(lockRootOf(null, folders)).isNull()
		// A lock being removed no longer claims its books.
		assertThat(lockRootOf(3, folders, removing = setOf(1L))).isNull()
	}

	@Test
	fun oneLockPerSubtree() {
		assertThat(lockCheck(4, folders)).isEqualTo(LockCheck.OK)
		assertThat(lockCheck(5, folders)).isEqualTo(LockCheck.OK)
		assertThat(lockCheck(1, folders)).isEqualTo(LockCheck.ALREADY_LOCKED)
		assertThat(lockCheck(2, folders)).isEqualTo(LockCheck.INSIDE_LOCKED)
		assertThat(lockCheck(99, folders)).isEqualTo(LockCheck.GONE)
		val withLockedChild = folders + (7L to FolderEntity(id = 7, parentId = 5, name = "x", lock = FolderLock.PIN, createdAt = 0))
		assertThat(lockCheck(4, withLockedChild)).isEqualTo(LockCheck.CONTAINS_LOCKED)
	}

	@Test
	fun lockedSubtreesCannotNest() {
		assertThat(folderMoveCheck(6, 2, folders)).isEqualTo(MoveCheck.NESTED_LOCK)
		assertThat(folderMoveCheck(6, 5, folders)).isEqualTo(MoveCheck.OK)
		assertThat(folderMoveCheck(4, 3, folders)).isEqualTo(MoveCheck.OK)
		assertThat(folderMoveCheck(1, 3, folders)).isEqualTo(MoveCheck.INTO_ITSELF)
		assertThat(folderMoveCheck(2, null, folders)).isEqualTo(MoveCheck.OK)
	}

	@Test
	fun subtreeHoldsEveryDescendant() {
		assertThat(subtree(1, folders.values)).containsExactly(1L, 2L, 3L)
		assertThat(subtree(5, folders.values)).containsExactly(5L)
	}

	@Test
	fun lockFallsDueAfterTheTimeout() {
		assertThat(lockDue(LockTimeout.ONE_MINUTE, backgroundedAt = null, now = 1_000_000)).isFalse()
		assertThat(lockDue(LockTimeout.ONE_MINUTE, backgroundedAt = 0, now = 59_999)).isFalse()
		assertThat(lockDue(LockTimeout.ONE_MINUTE, backgroundedAt = 0, now = 60_000)).isTrue()
		assertThat(lockDue(LockTimeout.IMMEDIATELY, backgroundedAt = 5, now = 5)).isTrue()
		assertThat(LockTimeout.entries.map { it.millis }).containsExactly(0L, 60_000L, 300_000L, 900_000L).inOrder()
	}
}
