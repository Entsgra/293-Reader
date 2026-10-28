package com.entsgra.reader.core.security

import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** What a [SecretBox] protects. Picks its Keystore keys, associated data and attempt counter. */
sealed class VaultId(internal val name: String) {
	class Folder(val folderId: Long) : VaultId("folder_$folderId")

	data object App : VaultId("app")

	internal val deviceAlias get() = "293_$name"
	internal val biometricAlias get() = "293_${name}_bio"
	internal val context get() = "293-reader/vault/v1/$name".toByteArray()
}

/** Outcome of anything that checks a PIN or passphrase. */
sealed interface SecretResult<out T> {
	class Ok<out T>(val value: T) : SecretResult<T>

	/** Wrong secret; the next attempt may come after [waitMs]. */
	data class Wrong(val waitMs: Long) : SecretResult<Nothing>

	/** Too many wrong attempts; nothing was tried. */
	data class Wait(val waitMs: Long) : SecretResult<Nothing>
}

inline fun <T, R> SecretResult<T>.map(transform: (T) -> R): SecretResult<R> = when (this) {
	is SecretResult.Ok -> SecretResult.Ok(transform(value))
	is SecretResult.Wrong -> this
	is SecretResult.Wait -> this
}

/**
 * Seals and opens payloads under a PIN or passphrase (always) and, optionally, biometrics. The
 * biometric wrap is a second, separate box around the same payload; it never replaces the
 * secret. Blocking; call from a background dispatcher.
 */
@Singleton
class Vault @Inject constructor(
	private val device: DeviceKeys,
	private val biometric: BiometricKeys,
	private val throttle: UnlockThrottle,
) {
	/** Zeroes [secret]. */
	fun seal(id: VaultId, payload: ByteArray, secret: CharArray, params: KdfParams = KdfParams.DEFAULT): SecretBox {
		val bytes = secretBytes(secret)
		try {
			return SecretWrap.seal(payload, bytes, device.aead(id.deviceAlias), id.context, params)
		} finally {
			bytes.fill(0)
			secret.fill('\u0000')
		}
	}

	/**
	 * Zeroes [secret]. Counts a wrong secret against the throttle. The payload in the result
	 * belongs to the caller, who zeroes it when done.
	 */
	fun open(id: VaultId, box: SecretBox, secret: CharArray): SecretResult<ByteArray> {
		val bytes = secretBytes(secret)
		secret.fill('\u0000')
		try {
			val wait = throttle.waitMs(id.name)
			if (wait > 0) return SecretResult.Wait(wait)
			return try {
				SecretResult.Ok(SecretWrap.open(box, bytes, device.aead(id.deviceAlias), id.context))
					.also { throttle.succeeded(id.name) }
			} catch (_: WrongSecretException) {
				SecretResult.Wrong(throttle.failed(id.name))
			}
		} finally {
			bytes.fill(0)
		}
	}

	fun waitMs(id: VaultId): Long = throttle.waitMs(id.name)

	/** A cipher for BiometricPrompt; pass it back to [biometricSeal] after the match. */
	fun biometricEnrollCipher(id: VaultId): Cipher = biometric.newEncryptCipher(id.biometricAlias)

	fun biometricSeal(id: VaultId, cipher: Cipher, payload: ByteArray): ByteArray =
		BiometricSeal.seal(cipher, payload, id.context)

	/** Throws [BiometricInvalidatedException] when enrollment changed since the wrap was made. */
	fun biometricOpenCipher(id: VaultId, sealed: ByteArray): Cipher = biometric.decryptCipher(id.biometricAlias, sealed)

	fun biometricOpen(id: VaultId, cipher: Cipher, sealed: ByteArray): ByteArray =
		BiometricSeal.open(cipher, sealed, id.context).also { throttle.succeeded(id.name) }

	fun removeBiometric(id: VaultId) = biometric.delete(id.biometricAlias)

	/** Deletes the Keystore keys and attempt count, once the box itself is gone. */
	fun forget(id: VaultId) {
		biometric.delete(id.biometricAlias)
		device.delete(id.deviceAlias)
		throttle.forget(id.name)
	}
}
