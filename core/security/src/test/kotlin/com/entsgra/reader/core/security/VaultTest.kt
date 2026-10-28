package com.entsgra.reader.core.security

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultTest {
	@get:Rule val tmp = TemporaryFolder()

	private val clock = FakeClock()
	private val biometric = FakeBiometricKeys()
	private val keyring = Keyring()
	private val folders by lazy { FolderVault(testVault(tmp.root, clock, biometric), keyring) }

	@Test
	fun createLeavesTheFolderUnlockedAndLockDropsTheKeys() {
		folders.create(7, "472913".toCharArray())
		val keys = keyring[7]!!
		assertThat(keyring.unlocked.value).containsExactly(7L)

		keyring.lockAll()

		assertThat(keyring[7]).isNull()
		assertThat(keyring.unlocked.value).isEmpty()
		assertThat(keys.payload.all { it == 0.toByte() }).isTrue()
		assertThrows(FolderLockedException::class.java) { keyring.require(7) }
	}

	@Test
	fun unlockRestoresTheSameKeys() {
		val box = folders.create(7, "472913".toCharArray())
		val sealed = keyring.require(7).seal("Austerlitz".toByteArray(), AD)
		val file = encrypt(keyring.require(7))
		keyring.lock(7)

		assertThat(folders.unlock(7, box, "472913".toCharArray())).isInstanceOf(SecretResult.Ok::class.java)

		assertThat(String(keyring.require(7).open(sealed, AD))).isEqualTo("Austerlitz")
		assertThat(decrypt(keyring.require(7), file)).isEqualTo(PLAIN)
	}

	@Test
	fun wrongSecretsCountAndThenWait() {
		val box = folders.create(7, "472913".toCharArray())
		keyring.lock(7)
		repeat(4) { assertThat(folders.unlock(7, box, "000000".toCharArray())).isEqualTo(SecretResult.Wrong(0)) }
		assertThat(folders.unlock(7, box, "000000".toCharArray())).isEqualTo(SecretResult.Wrong(30_000))
		// Even the right secret is not tried during the wait.
		assertThat(folders.unlock(7, box, "472913".toCharArray())).isEqualTo(SecretResult.Wait(30_000))
		assertThat(keyring[7]).isNull()
		clock.advance(30_000)
		assertThat(folders.unlock(7, box, "472913".toCharArray())).isInstanceOf(SecretResult.Ok::class.java)
		assertThat(folders.waitMs(7)).isEqualTo(0)
	}

	@Test
	fun rewrapChangesTheSecretButNotTheKeys() {
		val box = folders.create(7, "472913".toCharArray())
		val file = encrypt(keyring.require(7))
		keyring.lock(7)

		val rewrapped = folders.rewrap(7, box, "472913".toCharArray(), "a longer passphrase".toCharArray())
		val newBox = (rewrapped as SecretResult.Ok).value
		assertThat(newBox.salt).isNotEqualTo(box.salt)

		assertThat(folders.unlock(7, newBox, "472913".toCharArray())).isInstanceOf(SecretResult.Wrong::class.java)
		assertThat(folders.unlock(7, newBox, "a longer passphrase".toCharArray())).isInstanceOf(SecretResult.Ok::class.java)
		assertThat(decrypt(keyring.require(7), file)).isEqualTo(PLAIN)
	}

	@Test
	fun rewrapWithTheWrongSecretChangesNothing() {
		val box = folders.create(7, "472913".toCharArray())
		assertThat(folders.rewrap(7, box, "000000".toCharArray(), "999999".toCharArray()))
			.isInstanceOf(SecretResult.Wrong::class.java)
	}

	@Test
	fun biometricWrapOpensTheSameKeys() {
		val box = folders.create(7, "472913".toCharArray())
		val file = encrypt(keyring.require(7))
		val bio = folders.biometricEnroll(7, folders.biometricEnrollCipher(7))
		keyring.lock(7)

		folders.biometricUnlock(7, folders.biometricUnlockCipher(7, bio), bio)

		assertThat(decrypt(keyring.require(7), file)).isEqualTo(PLAIN)
		assertThat(box.sealed).isNotEqualTo(bio)
	}

	@Test
	fun invalidatedBiometricsFallBackToTheSecret() {
		val box = folders.create(7, "472913".toCharArray())
		val bio = folders.biometricEnroll(7, folders.biometricEnrollCipher(7))
		keyring.lock(7)
		biometric.invalidate()

		assertThrows(BiometricInvalidatedException::class.java) { folders.biometricUnlockCipher(7, bio) }
		assertThat(folders.unlock(7, box, "472913".toCharArray())).isInstanceOf(SecretResult.Ok::class.java)
		// Re-enrolling makes a new wrap that works again.
		val again = folders.biometricEnroll(7, folders.biometricEnrollCipher(7))
		keyring.lock(7)
		folders.biometricUnlock(7, folders.biometricUnlockCipher(7, again), again)
		assertThat(keyring[7]).isNotNull()
	}

	@Test
	fun forgetDeletesTheKeystoreKeys() {
		val box = folders.create(7, "472913".toCharArray())
		folders.forget(7)
		assertThat(keyring[7]).isNull()
		// The device-layer key is gone, so even the right secret no longer opens the box.
		assertThrows(java.security.GeneralSecurityException::class.java) {
			folders.unlock(7, box, "472913".toCharArray())
		}
	}

	@Test
	fun secretArraysAreZeroed() {
		val secret = "472913".toCharArray()
		folders.create(7, secret)
		assertThat(secret.all { it == '\u0000' }).isTrue()
	}

	private fun encrypt(keys: FolderKeys): ByteArray {
		val out = ByteArrayOutputStream()
		keys.files.newEncryptingStream(out, AD).use { it.write(PLAIN) }
		return out.toByteArray()
	}

	private fun decrypt(keys: FolderKeys, file: ByteArray): ByteArray =
		keys.files.newDecryptingStream(file.inputStream(), AD).use { it.readBytes() }

	private companion object {
		val AD = "293-reader/file/v1/abc".toByteArray()
		val PLAIN = ByteArray(5000) { (it * 7).toByte() }
	}
}
