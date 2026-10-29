package com.entsgra.reader.core.data

import android.content.Context
import com.entsgra.reader.core.data.library.BookFiles
import com.entsgra.reader.core.data.library.BookImporter
import com.entsgra.reader.core.data.library.CoverStore
import com.entsgra.reader.core.data.library.LibraryRepository
import com.entsgra.reader.core.data.library.StorageLock
import com.entsgra.reader.core.data.lock.FolderLocks
import com.entsgra.reader.core.data.lock.Rekeyer
import com.entsgra.reader.core.data.lock.Sealer
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.security.AppLockStore
import com.entsgra.reader.core.security.BiometricKeys
import com.entsgra.reader.core.security.BiometricInvalidatedException
import com.entsgra.reader.core.security.DatabaseKey
import com.entsgra.reader.core.security.DeviceKeys
import com.entsgra.reader.core.security.FileCipher
import com.entsgra.reader.core.security.FolderVault
import com.entsgra.reader.core.security.Keyring
import com.entsgra.reader.core.security.KeystoreCryptoPrimitives
import com.entsgra.reader.core.security.UnlockThrottle
import com.entsgra.reader.core.security.Vault
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookFormatRegistry
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The data layer wired by hand, as Hilt would, over the real Keystore, Tink and SQLCipher. */
class TestGraph(val context: Context, val biometric: BiometricKeys = SoftBiometricKeys()) {
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	val appLock = AppLockStore(context)
	val crypto = KeystoreCryptoPrimitives(context, appLock)
	val keyring = Keyring()
	val cipher = FileCipher(crypto, keyring)
	val database = DatabaseProvider(context, DatabaseKey(context, crypto, appLock), appLock)
	val files = BookFiles(context)
	val covers = CoverStore(files, cipher)
	val sealer = Sealer(keyring)
	val storage = StorageLock()
	val vault = FolderVault(Vault(KeystoreKeys, biometric, UnlockThrottle(context)), keyring)
	val rekeyer = Rekeyer(database, files, cipher, sealer, keyring, vault, storage)
	val folderLocks = FolderLocks(database, vault, keyring, rekeyer, covers, BitmapPool(1_000_000), scope)
	val library = LibraryRepository(database, files, covers, sealer, keyring, vault, rekeyer, scope)

	fun importer(formats: BookFormatRegistry = BookFormatRegistry(emptySet())) =
		BookImporter(context, database, files, cipher, covers, formats, sealer, keyring, storage, scope)

	/** Wipes what earlier tests left: rows, files and attempt counts. */
	suspend fun reset() {
		database.get().clearAllTables()
		File(context.noBackupFilesDir, "books").deleteRecursively()
		File(context.noBackupFilesDir, "covers").deleteRecursively()
		File(context.noBackupFilesDir, "unlock_attempts").delete()
		keyring.lockAll()
	}
}

/** The device layer on the real Keystore, as the app's own binding does. */
private object KeystoreKeys : DeviceKeys {
	override fun aead(alias: String): Aead {
		if (!AndroidKeystore.hasKey(alias)) AndroidKeystore.generateNewAes256GcmKey(alias)
		return AndroidKeystore.getAead(alias)
	}

	override fun delete(alias: String) {
		if (AndroidKeystore.hasKey(alias)) AndroidKeystore.deleteKey(alias)
	}
}

/**
 * Biometric keys without a sensor: the emulator cannot pass a BIOMETRIC_STRONG prompt from a
 * test, nor enroll a new finger. [invalidate] models what Android does on a new enrollment.
 */
class SoftBiometricKeys : BiometricKeys {
	private val keys = HashMap<String, SecretKey>()

	override fun newEncryptCipher(alias: String): Cipher {
		val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
		keys[alias] = key
		return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
	}

	override fun decryptCipher(alias: String, sealed: ByteArray): Cipher {
		val key = keys[alias] ?: throw BiometricInvalidatedException()
		return Cipher.getInstance("AES/GCM/NoPadding")
			.apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
	}

	override fun delete(alias: String) {
		keys.remove(alias)
	}

	fun invalidate() = keys.clear()
}
