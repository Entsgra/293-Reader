package com.entsgra.reader.core.security

import com.google.crypto.tink.Aead
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A payload protected by a PIN or passphrase, in two layers: AES-256-GCM under a key derived
 * from the secret with Argon2id, then AES-GCM again under a non-exportable Keystore key with no
 * user-auth requirement. Opening it takes the secret and this device: the blob copied off the
 * phone cannot be attacked without the Keystore key, and the Keystore key alone still leaves
 * the Argon2id layer.
 */
class SecretBox(val params: KdfParams, val salt: ByteArray, val sealed: ByteArray)

/** The secret did not open the box. */
class WrongSecretException : GeneralSecurityException("Wrong PIN or passphrase")

internal object SecretWrap {
	private val random = SecureRandom()

	/** [context] names what the payload is, so a box cannot be swapped for another one. */
	fun seal(
		payload: ByteArray,
		secret: ByteArray,
		device: Aead,
		context: ByteArray,
		params: KdfParams = KdfParams.DEFAULT,
	): SecretBox {
		require(params.isStrongEnough) { "KDF parameters below the floor" }
		val salt = ByteArray(Argon2id.SALT_BYTES).also(random::nextBytes)
		val inner = sealInner(payload, secret, salt, params, context)
		return SecretBox(params, salt, device.encrypt(inner, context))
	}

	/**
	 * Throws [WrongSecretException] for a wrong secret, and another [GeneralSecurityException]
	 * when the Keystore layer fails: a different device, or a box that was tampered with.
	 */
	fun open(box: SecretBox, secret: ByteArray, device: Aead, context: ByteArray): ByteArray {
		if (!box.params.isStrongEnough) throw GeneralSecurityException("KDF parameters below the floor")
		val inner = device.decrypt(box.sealed, context)
		return openInner(inner, secret, box.salt, box.params, context)
	}

	/** The Argon2id layer alone, exposed for tests that model a dump with the Keystore layer gone. */
	fun sealInner(payload: ByteArray, secret: ByteArray, salt: ByteArray, params: KdfParams, context: ByteArray): ByteArray {
		val kek = Argon2id.derive(secret, salt, params)
		try {
			return AesGcm.encrypt(kek, payload, innerContext(context, params))
		} finally {
			kek.fill(0)
		}
	}

	fun openInner(inner: ByteArray, secret: ByteArray, salt: ByteArray, params: KdfParams, context: ByteArray): ByteArray {
		val kek = Argon2id.derive(secret, salt, params)
		try {
			return AesGcm.decrypt(kek, inner, innerContext(context, params))
		} catch (_: AEADBadTagException) {
			throw WrongSecretException()
		} finally {
			kek.fill(0)
		}
	}

	/** Binds the cost, so lowering it in storage breaks the box instead of weakening it. */
	private fun innerContext(context: ByteArray, params: KdfParams): ByteArray =
		context + "/argon2id/${params.memoryKib}/${params.iterations}/${params.parallelism}".toByteArray()
}

/** AES-256-GCM with a random 96-bit nonce stored in front of the ciphertext. */
internal object AesGcm {
	private const val NONCE_BYTES = 12
	private const val TAG_BITS = 128
	private val random = SecureRandom()

	fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
		val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
		cipher.updateAAD(associatedData)
		return nonce + cipher.doFinal(plaintext)
	}

	fun decrypt(key: ByteArray, sealed: ByteArray, associatedData: ByteArray): ByteArray {
		if (sealed.size < NONCE_BYTES) throw AEADBadTagException("Too short")
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
		cipher.updateAAD(associatedData)
		return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
	}
}
