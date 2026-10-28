package com.entsgra.reader.core.model

data class Folder(
	val id: Long,
	val parentId: Long?,
	val name: String,
	val lock: FolderLock,
	val createdAt: Long,
)

/** How a folder is unlocked. Anything other than [NONE] hides its content while locked. */
enum class FolderLock {
	NONE,

	/** Biometric (BIOMETRIC_STRONG) or device credential, per-use Keystore auth. */
	DEVICE,

	/** User passphrase layered on the Keystore key. */
	PASSPHRASE,

	/** Device auth and passphrase, both required. */
	DEVICE_AND_PASSPHRASE,
}
