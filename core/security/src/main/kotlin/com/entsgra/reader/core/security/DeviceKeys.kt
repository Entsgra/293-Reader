package com.entsgra.reader.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-exportable AES-GCM keys in Android Keystore that need no user auth: the device layer of a
 * [SecretBox]. Blocking; call from a background dispatcher.
 */
interface DeviceKeys {
	/** The key under [alias], made on first use. */
	fun aead(alias: String): Aead

	fun delete(alias: String)
}

@Singleton
internal class KeystoreDeviceKeys @Inject constructor() : DeviceKeys {
	@Synchronized
	override fun aead(alias: String): Aead {
		if (!AndroidKeystore.hasKey(alias)) AndroidKeystore.generateNewAes256GcmKey(alias)
		return AndroidKeystore.getAead(alias)
	}

	@Synchronized
	override fun delete(alias: String) {
		if (AndroidKeystore.hasKey(alias)) AndroidKeystore.deleteKey(alias)
	}
}

/** The biometric key is gone: new fingerprints were enrolled, or the screen lock was removed. */
class BiometricInvalidatedException(cause: Throwable? = null) :
	GeneralSecurityException("Biometric key invalidated", cause)

/**
 * Keystore keys that only work right after a BIOMETRIC_STRONG match, and that Android deletes
 * when new biometrics are enrolled. Each cipher handed out goes to BiometricPrompt as its
 * CryptoObject; [seal] and [open] run once the prompt succeeds.
 */
interface BiometricKeys {
	/** Makes a new key under [alias], replacing any old one, and a cipher to encrypt with it. */
	fun newEncryptCipher(alias: String): Cipher

	/** Throws [BiometricInvalidatedException] when the key no longer exists or works. */
	fun decryptCipher(alias: String, sealed: ByteArray): Cipher

	fun delete(alias: String)
}

internal object BiometricSeal {
	private const val NONCE_BYTES = 12
	const val TAG_BITS = 128

	fun seal(cipher: Cipher, payload: ByteArray, context: ByteArray): ByteArray {
		cipher.updateAAD(context)
		return cipher.iv + cipher.doFinal(payload)
	}

	fun open(cipher: Cipher, sealed: ByteArray, context: ByteArray): ByteArray {
		cipher.updateAAD(context)
		return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
	}

	fun nonce(sealed: ByteArray): GCMParameterSpec {
		if (sealed.size < NONCE_BYTES) throw BiometricInvalidatedException()
		return GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES)
	}
}

@Singleton
internal class KeystoreBiometricKeys @Inject constructor(
	@ApplicationContext private val context: Context,
) : BiometricKeys {
	override fun newEncryptCipher(alias: String): Cipher {
		delete(alias)
		val key = generate(alias, strongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
		return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
	}

	override fun decryptCipher(alias: String, sealed: ByteArray): Cipher {
		val key = try {
			keyStore().getKey(alias, null) as? SecretKey
		} catch (e: UnrecoverableKeyException) {
			throw BiometricInvalidatedException(e)
		} ?: throw BiometricInvalidatedException()
		return try {
			Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, BiometricSeal.nonce(sealed)) }
		} catch (e: KeyPermanentlyInvalidatedException) {
			throw BiometricInvalidatedException(e)
		}
	}

	override fun delete(alias: String) {
		val store = keyStore()
		if (store.containsAlias(alias)) store.deleteEntry(alias)
	}

	private fun generate(alias: String, strongBox: Boolean): SecretKey {
		val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
			.setKeySize(KEY_BITS)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setUserAuthenticationRequired(true)
			// 0: every use needs its own match, passed in through the prompt's CryptoObject.
			.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
			.setInvalidatedByBiometricEnrollment(true)
			.setIsStrongBoxBacked(strongBox)
			.build()
		return try {
			KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
		} catch (e: StrongBoxUnavailableException) {
			if (!strongBox) throw e
			generate(alias, strongBox = false)
		}
	}

	private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

	private companion object {
		const val KEYSTORE = "AndroidKeyStore"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val KEY_BITS = 256
	}
}
