package com.entsgra.reader.core.model

data class Folder(
	val id: Long,
	val parentId: Long?,
	val name: String,
	val lock: FolderLock,
	val createdAt: Long,
)

/**
 * Whether a folder is locked, and with what. A locked folder's books, covers and subfolder
 * names are encrypted under its own key, which only its secret (or biometrics, when set up on
 * top) can open.
 */
enum class FolderLock {
	NONE,

	/** Six or more digits. */
	PIN,

	/** Eight or more characters. */
	PASSPHRASE,
}

/** The kind of secret that guards a locked folder or the app lock. */
enum class SecretKind { PIN, PASSPHRASE }

fun SecretKind.asFolderLock(): FolderLock = when (this) {
	SecretKind.PIN -> FolderLock.PIN
	SecretKind.PASSPHRASE -> FolderLock.PASSPHRASE
}

fun FolderLock.secretKind(): SecretKind? = when (this) {
	FolderLock.NONE -> null
	FolderLock.PIN -> SecretKind.PIN
	FolderLock.PASSPHRASE -> SecretKind.PASSPHRASE
}

/** How long the app may sit in the background before locked folders (and the app lock) close. */
enum class LockTimeout(val millis: Long) {
	IMMEDIATELY(0),
	ONE_MINUTE(60_000),
	FIVE_MINUTES(5 * 60_000),
	FIFTEEN_MINUTES(15 * 60_000),
}
