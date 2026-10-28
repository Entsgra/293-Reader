package com.entsgra.reader.core.security

import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * The app-wide lock. Turning it on moves the database passphrase and the app's file keyset out
 * of their Keystore-only wraps into a [SecretBox] (secret plus a Keystore layer); no book is
 * re-encrypted. Each step writes the new copy durably before the old one is deleted, so a crash
 * leaves at least one usable copy; while `app.lock` exists it wins, and leftovers are deleted at
 * the next unlock. Blocking; call from a background dispatcher.
 */
@Singleton
class AppLock @Inject constructor(
	private val store: AppLockStore,
	private val vault: Vault,
	private val databaseKey: DatabaseKey,
	private val primitives: KeystoreCryptoPrimitives,
) {
	val isEnabled: Boolean get() = store.isEnabled

	/** True when there is no app lock, or it was opened in this process. */
	val isOpen: StateFlow<Boolean> = store.isOpen

	/** True for a PIN, false for a passphrase; null without an app lock. */
	fun usesPin(): Boolean? = store.read()?.pin

	fun hasBiometric(): Boolean = store.read()?.biometric != null

	fun waitMs(): Long = vault.waitMs(VaultId.App)

	/** Zeroes [secret]. */
	@Synchronized
	fun enable(pin: Boolean, secret: CharArray) {
		check(!store.isEnabled) { "App lock already on" }
		val passphrase = databaseKey.passphrase()
		val keyset = primitives.exportFilesKeyset()
		val payload = AppSecrets.encode(passphrase, keyset)
		passphrase.fill(0)
		keyset.fill(0)
		store.write(AppLockRecord(pin, vault.seal(VaultId.App, payload, secret), biometric = null))
		store.open(payload)
		deleteKeystoreOnlyCopies()
	}

	@Synchronized
	fun unlock(secret: CharArray): SecretResult<Unit> {
		val record = store.read() ?: return SecretResult.Ok(Unit)
		return vault.open(VaultId.App, record.box, secret).map(::opened)
	}

	/** Throws [BiometricInvalidatedException] when enrollment changed; fall back to the secret. */
	fun biometricUnlockCipher(): Cipher {
		val sealed = store.read()?.biometric ?: throw BiometricInvalidatedException()
		return vault.biometricOpenCipher(VaultId.App, sealed)
	}

	@Synchronized
	fun biometricUnlock(cipher: Cipher) {
		val sealed = store.read()?.biometric ?: throw BiometricInvalidatedException()
		opened(vault.biometricOpen(VaultId.App, cipher, sealed))
	}

	fun biometricEnrollCipher(): Cipher = vault.biometricEnrollCipher(VaultId.App)

	/** Adds the biometric wrap; the app lock must be open. */
	@Synchronized
	fun biometricEnroll(cipher: Cipher) {
		val record = store.read() ?: return
		val secrets = store.secrets ?: throw AppLockedException()
		store.write(AppLockRecord(record.pin, record.box, vault.biometricSeal(VaultId.App, cipher, secrets.payload)))
	}

	@Synchronized
	fun removeBiometric() {
		val record = store.read() ?: return
		store.write(AppLockRecord(record.pin, record.box, biometric = null))
		vault.removeBiometric(VaultId.App)
	}

	/** Re-wraps the same keys under a new secret. Zeroes both secrets. */
	@Synchronized
	fun changeSecret(current: CharArray, pin: Boolean, new: CharArray): SecretResult<Unit> {
		val record = store.read() ?: return SecretResult.Ok(Unit).also { new.fill('\u0000') }
		return vault.open(VaultId.App, record.box, current).map { payload ->
			try {
				store.write(AppLockRecord(pin, vault.seal(VaultId.App, payload, new), record.biometric))
			} finally {
				payload.fill(0)
			}
		}.also { new.fill('\u0000') }
	}

	/** Puts the keys back under Keystore alone. Zeroes [secret]. */
	@Synchronized
	fun disable(secret: CharArray): SecretResult<Unit> {
		val record = store.read() ?: return SecretResult.Ok(Unit)
		return vault.open(VaultId.App, record.box, secret).map { payload ->
			val secrets = AppSecrets(payload)
			databaseKey.store(secrets.databasePassphrase)
			primitives.restoreFilesKeyset(secrets.filesKeyset)
			store.delete()
			vault.forget(VaultId.App)
			payload.fill(0)
		}
	}

	private fun opened(payload: ByteArray) {
		store.open(payload)
		// A crash while the lock was being turned on can leave the old copies behind.
		deleteKeystoreOnlyCopies()
	}

	private fun deleteKeystoreOnlyCopies() {
		databaseKey.deleteStored()
		primitives.deleteStoredFilesKeyset()
	}
}
