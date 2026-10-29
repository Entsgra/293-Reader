package com.entsgra.reader.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app lock on a device: real Keystore, the Tink prefs keyset and the database key file.
 * Each [Process] is what a fresh start of the app would build.
 */
@RunWith(AndroidJUnit4::class)
class AppLockTest {
	private val context = ApplicationProvider.getApplicationContext<Context>()
	private val plain = Random(293).nextBytes(100_000)

	private inner class Process {
		val store = AppLockStore(context)
		val primitives = KeystoreCryptoPrimitives(context, store)
		val databaseKey = DatabaseKey(context, primitives, store)
		val cipher = FileCipher(primitives, Keyring())
		val appLock = AppLock(store, Vault(KeystoreDeviceKeys(), KeystoreBiometricKeys(context), UnlockThrottle(context)), databaseKey, primitives)
	}

	@Before
	fun clean() {
		File(context.noBackupFilesDir, "app.lock").delete()
		File(context.noBackupFilesDir, "db.key").delete()
		File(context.noBackupFilesDir, "unlock_attempts").delete()
		context.getSharedPreferences("reader_keysets", Context.MODE_PRIVATE).edit().clear().commit()
	}

	@Test
	fun lockHoldsTheOnlyCopiesAndOffPutsThemBack() {
		val first = Process()
		val passphrase = first.databaseKey.passphrase()
		val book = File(context.cacheDir, "book").also { first.cipher.encrypt(plain.inputStream(), it) }

		first.appLock.enable(pin = true, secret = PIN.toCharArray())

		assertThat(File(context.noBackupFilesDir, "db.key").exists()).isFalse()
		assertThat(context.getSharedPreferences("reader_keysets", Context.MODE_PRIVATE).contains("keyset_files")).isFalse()
		// The running process keeps working.
		assertThat(first.databaseKey.passphrase()).isEqualTo(passphrase)

		val second = Process()
		assertThat(second.appLock.isOpen.value).isFalse()
		assertThrows(AppLockedException::class.java) { second.databaseKey.passphrase() }
		assertThrows(AppLockedException::class.java) { second.cipher.openInputStream(book) }
		assertThat(second.appLock.unlock("000000".toCharArray())).isInstanceOf(SecretResult.Wrong::class.java)
		assertThat(second.appLock.unlock(PIN.toCharArray())).isInstanceOf(SecretResult.Ok::class.java)
		assertThat(second.appLock.isOpen.value).isTrue()
		assertThat(second.databaseKey.passphrase()).isEqualTo(passphrase)
		assertThat(second.cipher.openInputStream(book).use { it.readBytes() }).isEqualTo(plain)

		assertThat(second.appLock.disable(PIN.toCharArray())).isInstanceOf(SecretResult.Ok::class.java)

		val third = Process()
		assertThat(third.appLock.isEnabled).isFalse()
		assertThat(third.databaseKey.passphrase()).isEqualTo(passphrase)
		assertThat(third.cipher.openInputStream(book).use { it.readBytes() }).isEqualTo(plain)
	}

	@Test
	fun theLockFileAloneOpensNothingWithoutThisDevicesKey() {
		val first = Process()
		first.databaseKey.passphrase()
		first.appLock.enable(pin = false, secret = PASSPHRASE.toCharArray())
		val record = first.store.read()!!

		// Another device: same file, same passphrase, but its Keystore never held the outer key.
		KeystoreDeviceKeys().delete(VaultId.App.deviceAlias)
		assertThrows(java.security.GeneralSecurityException::class.java) {
			SecretWrap.open(record.box, PASSPHRASE.toByteArray(), KeystoreDeviceKeys().aead(VaultId.App.deviceAlias), VaultId.App.context)
		}
	}

	@Test
	fun aCrashAfterTheLockIsWrittenKeepsTheLockAndCleansUpAtUnlock() {
		val first = Process()
		val passphrase = first.databaseKey.passphrase()
		val keyset = first.primitives.exportFilesKeyset()
		// What enable() writes before deleting the Keystore-only copies; the process dies there.
		first.store.write(
			AppLockRecord(
				pin = true,
				box = first.appLockVault().seal(VaultId.App, AppSecrets.encode(passphrase, keyset), PIN.toCharArray()),
				biometric = null,
			),
		)
		assertThat(File(context.noBackupFilesDir, "db.key").exists()).isTrue()

		val second = Process()
		assertThrows(AppLockedException::class.java) { second.databaseKey.passphrase() }
		assertThat(second.appLock.unlock(PIN.toCharArray())).isInstanceOf(SecretResult.Ok::class.java)
		assertThat(second.databaseKey.passphrase()).isEqualTo(passphrase)
		assertThat(File(context.noBackupFilesDir, "db.key").exists()).isFalse()
	}

	private fun Process.appLockVault() = Vault(KeystoreDeviceKeys(), KeystoreBiometricKeys(context), UnlockThrottle(context))

	private companion object {
		const val PIN = "472913"
		const val PASSPHRASE = "a longer passphrase"
	}
}
