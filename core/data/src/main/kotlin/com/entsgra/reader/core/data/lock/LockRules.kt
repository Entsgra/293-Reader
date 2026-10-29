package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.LockTimeout
import com.entsgra.reader.core.model.SecretKind

enum class SecretCheck { OK, PIN_TOO_SHORT, PIN_NOT_DIGITS, PASSPHRASE_TOO_SHORT, MISMATCH }

const val MIN_PIN_DIGITS = 6
const val MIN_PASSPHRASE_CHARS = 8

/** Whether [secret] is acceptable for [kind], and typed the same twice. */
fun checkSecret(kind: SecretKind, secret: CharSequence, confirm: CharSequence): SecretCheck = when {
	kind == SecretKind.PIN && secret.any { it !in '0'..'9' } -> SecretCheck.PIN_NOT_DIGITS
	kind == SecretKind.PIN && secret.length < MIN_PIN_DIGITS -> SecretCheck.PIN_TOO_SHORT
	kind == SecretKind.PASSPHRASE && Character.codePointCount(secret, 0, secret.length) < MIN_PASSPHRASE_CHARS ->
		SecretCheck.PASSPHRASE_TOO_SHORT
	secret.toString() != confirm.toString() -> SecretCheck.MISMATCH
	else -> SecretCheck.OK
}

/** Why a lock cannot be set on a folder. */
enum class LockCheck { OK, ALREADY_LOCKED, INSIDE_LOCKED, CONTAINS_LOCKED, GONE }

/** Outcome of moving a book or folder. */
enum class MoveCheck { OK, INTO_ITSELF, NESTED_LOCK, LOCKED }

/**
 * The locked folder whose key covers [folderId]: the folder itself or its nearest locked
 * ancestor; null for the app key. Folders in [removing] are on their way back to the app key.
 */
internal fun lockRootOf(folderId: Long?, byId: Map<Long, FolderEntity>, removing: Set<Long> = emptySet()): Long? {
	var id = folderId
	var steps = 0
	while (id != null) {
		val folder = byId[id] ?: return null
		if (folder.lock != FolderLock.NONE && id !in removing) return id
		id = folder.parentId
		// A corrupt cycle would loop forever.
		if (++steps > byId.size) return null
	}
	return null
}

/** One lock per subtree: no locked folder above [folderId], none at it, none below. */
internal fun lockCheck(folderId: Long, byId: Map<Long, FolderEntity>): LockCheck {
	val folder = byId[folderId] ?: return LockCheck.GONE
	if (folder.lock != FolderLock.NONE) return LockCheck.ALREADY_LOCKED
	if (lockRootOf(folder.parentId, byId) != null) return LockCheck.INSIDE_LOCKED
	val below = subtree(folderId, byId.values) - folderId
	if (below.any { byId[it]?.lock != FolderLock.NONE }) return LockCheck.CONTAINS_LOCKED
	return LockCheck.OK
}

/** [folderId] and every folder below it. */
internal fun subtree(folderId: Long, folders: Collection<FolderEntity>): Set<Long> {
	val children = folders.groupBy { it.parentId }
	val out = LinkedHashSet<Long>()
	val queue = ArrayDeque(listOf(folderId))
	while (queue.isNotEmpty()) {
		val id = queue.removeFirst()
		if (out.add(id)) children[id]?.forEach { queue.addLast(it.id) }
	}
	return out
}

/**
 * Whether a folder may move under [parentId]. A subtree holding a lock cannot go inside another
 * locked folder, since a book is under one folder key at most.
 */
internal fun folderMoveCheck(folderId: Long, parentId: Long?, byId: Map<Long, FolderEntity>): MoveCheck {
	val moving = subtree(folderId, byId.values)
	if (parentId != null && parentId in moving) return MoveCheck.INTO_ITSELF
	val holdsLock = moving.any { byId[it]?.lock != FolderLock.NONE }
	if (holdsLock && lockRootOf(parentId, byId) != null) return MoveCheck.NESTED_LOCK
	return MoveCheck.OK
}

/** True once the app has been in the background for [timeout]. */
internal fun lockDue(timeout: LockTimeout, backgroundedAt: Long?, now: Long): Boolean =
	backgroundedAt != null && now - backgroundedAt >= timeout.millis
