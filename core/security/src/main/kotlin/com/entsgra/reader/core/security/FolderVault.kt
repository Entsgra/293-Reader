package com.entsgra.reader.core.security

import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locked folders' keys: made when a lock is set, opened into the [Keyring] on unlock. The
 * caller stores the [SecretBox] and the biometric wrap. Blocking; call from a background
 * dispatcher.
 */
@Singleton
class FolderVault @Inject constructor(
	private val vault: Vault,
	private val keyring: Keyring,
) {
	/** Makes new keys for [folderId] under [secret] and leaves the folder unlocked. */
	fun create(folderId: Long, secret: CharArray): SecretBox {
		val id = VaultId.Folder(folderId)
		// Keystore keys a crash left from an earlier attempt would only confuse a later unlock.
		vault.forget(id)
		val payload = FolderKeys.generate()
		val box = vault.seal(id, payload, secret)
		keyring.put(folderId, FolderKeys.load(payload))
		return box
	}

	fun unlock(folderId: Long, box: SecretBox, secret: CharArray): SecretResult<Unit> =
		vault.open(VaultId.Folder(folderId), box, secret).map { keyring.put(folderId, FolderKeys.load(it)) }

	/** Checks [secret] without changing what is unlocked: confirming before a lock is removed. */
	fun check(folderId: Long, box: SecretBox, secret: CharArray): SecretResult<Unit> =
		vault.open(VaultId.Folder(folderId), box, secret).map { it.fill(0) }

	/** The same keys under a new secret. Nothing encrypted with them changes. */
	fun rewrap(folderId: Long, box: SecretBox, current: CharArray, new: CharArray): SecretResult<SecretBox> {
		val id = VaultId.Folder(folderId)
		return vault.open(id, box, current).map { payload ->
			try {
				vault.seal(id, payload, new)
			} finally {
				payload.fill(0)
			}
		}.also { new.fill('\u0000') }
	}

	fun waitMs(folderId: Long): Long = vault.waitMs(VaultId.Folder(folderId))

	fun biometricEnrollCipher(folderId: Long): Cipher = vault.biometricEnrollCipher(VaultId.Folder(folderId))

	/** The biometric wrap of an unlocked folder's keys, after the prompt matched. */
	fun biometricEnroll(folderId: Long, cipher: Cipher): ByteArray =
		vault.biometricSeal(VaultId.Folder(folderId), cipher, keyring.require(folderId).payload)

	/** Throws [BiometricInvalidatedException] when enrollment changed; fall back to the secret. */
	fun biometricUnlockCipher(folderId: Long, sealed: ByteArray): Cipher =
		vault.biometricOpenCipher(VaultId.Folder(folderId), sealed)

	fun biometricUnlock(folderId: Long, cipher: Cipher, sealed: ByteArray) {
		keyring.put(folderId, FolderKeys.load(vault.biometricOpen(VaultId.Folder(folderId), cipher, sealed)))
	}

	fun removeBiometric(folderId: Long) = vault.removeBiometric(VaultId.Folder(folderId))

	/** After the lock is removed: drops the keys and deletes their Keystore wraps. */
	fun forget(folderId: Long) {
		keyring.lock(folderId)
		vault.forget(VaultId.Folder(folderId))
	}
}
