package com.entsgra.reader.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SQLCipher passphrase: 32 random bytes, stored only wrapped by the Keystore-backed AEAD,
 * in no-backup storage so it never leaves the device.
 */
@Singleton
class DatabaseKey internal constructor(
	private val file: File,
	private val crypto: CryptoPrimitives,
) {
	@Inject
	constructor(
		@ApplicationContext context: Context,
		crypto: CryptoPrimitives,
	) : this(File(context.noBackupFilesDir, FILE_NAME), crypto)

	/** Blocking. Returns a fresh copy; the caller should zero it once the database is open. */
	@Synchronized
	fun passphrase(): ByteArray {
		if (file.exists()) {
			return crypto.aead.decrypt(file.readBytes(), ASSOCIATED_DATA)
		}
		val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
		val temp = File(file.parentFile, "${file.name}.part")
		writeDurably(temp) { it.write(crypto.aead.encrypt(key, ASSOCIATED_DATA)) }
		check(temp.renameTo(file)) { "Could not store database key" }
		return key
	}

	private companion object {
		const val FILE_NAME = "db.key"
		const val KEY_BYTES = 32
		val ASSOCIATED_DATA = "293-reader/db-key/v1".toByteArray()
	}
}
