package com.entsgra.reader.core.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Software stand-in for Keystore: one AES-GCM key per alias, gone when deleted. */
class FakeDeviceKeys : DeviceKeys {
	private val keys = HashMap<String, Aead>()

	override fun aead(alias: String): Aead = keys.getOrPut(alias) {
		Tink.register
		KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
	}

	override fun delete(alias: String) {
		keys.remove(alias)
	}
}

/** Biometric keys without the prompt; [invalidate] models a new fingerprint being enrolled. */
class FakeBiometricKeys : BiometricKeys {
	private val keys = HashMap<String, SecretKey>()

	override fun newEncryptCipher(alias: String): Cipher {
		val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
		keys[alias] = key
		return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
	}

	override fun decryptCipher(alias: String, sealed: ByteArray): Cipher {
		val key = keys[alias] ?: throw BiometricInvalidatedException()
		return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, BiometricSeal.nonce(sealed)) }
	}

	override fun delete(alias: String) {
		keys.remove(alias)
	}

	fun invalidate() = keys.clear()
}

class FakeClock(var wall: Long = 1_000_000_000L, var elapsed: Long = 50_000L, var boot: Int = 1) : ThrottleClock {
	override fun wallMs() = wall

	override fun elapsedMs() = elapsed

	override fun bootCount() = boot

	fun advance(ms: Long) {
		wall += ms
		elapsed += ms
	}
}

/** Cheap enough for tests while still passing the floor check. */
val TEST_PARAMS = KdfParams.FLOOR

fun testVault(dir: File, clock: ThrottleClock = FakeClock(), biometric: BiometricKeys = FakeBiometricKeys()) =
	Vault(FakeDeviceKeys(), biometric, UnlockThrottle(File(dir, "attempts"), clock))
