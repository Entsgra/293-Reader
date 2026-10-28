package com.entsgra.reader.core.security

import android.content.Context
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Something needed the app's keys while the app lock is closed. */
class AppLockedException : IllegalStateException("The app is locked")

/** The app lock as stored: which keyboard to show, the sealed keys and the optional biometric wrap. */
class AppLockRecord(val pin: Boolean, val box: SecretBox, val biometric: ByteArray?)

/** The keys the app lock protects, once opened: the database passphrase and the app's file keyset. */
internal class AppSecrets(val payload: ByteArray) {
	val databasePassphrase: ByteArray
	val filesKeyset: KeysetHandle

	init {
		val data = DataInputStream(payload.inputStream())
		databasePassphrase = ByteArray(data.readInt()).also(data::readFully)
		val keyset = ByteArray(data.readInt()).also(data::readFully)
		Tink.register
		filesKeyset = TinkProtoKeysetFormat.parseKeyset(keyset, InsecureSecretKeyAccess.get())
		keyset.fill(0)
	}

	companion object {
		fun encode(databasePassphrase: ByteArray, filesKeyset: ByteArray): ByteArray {
			val out = ByteArrayOutputStream()
			DataOutputStream(out).use { data ->
				data.writeInt(databasePassphrase.size)
				data.write(databasePassphrase)
				data.writeInt(filesKeyset.size)
				data.write(filesKeyset)
			}
			return out.toByteArray()
		}
	}
}

/**
 * Where the app lock lives: `app.lock` in no-backup storage. While it exists, the database
 * passphrase and the app's file keyset are stored only inside it, and nothing can open the
 * database or a book outside locked folders until [AppLock] opens it. Once opened the keys stay
 * in memory for the life of the process; locking the app again ends the process.
 */
@Singleton
class AppLockStore internal constructor(private val file: File) {
	@Inject
	constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, FILE_NAME))

	@Volatile
	internal var secrets: AppSecrets? = null
		private set

	private val _isOpen = MutableStateFlow(!file.exists())

	/** True when there is no app lock, or it has been opened in this process. */
	val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

	val isEnabled: Boolean get() = file.exists()

	/** Suspends until the app's keys can be used. */
	suspend fun awaitOpen() {
		isOpen.first { it }
	}

	@Synchronized
	internal fun read(): AppLockRecord? {
		if (!file.exists()) return null
		return try {
			DataInputStream(file.inputStream().buffered()).use { data ->
				if (data.readUnsignedByte() != VERSION) throw IOException("Unknown app lock version")
				val pin = data.readBoolean()
				val params = KdfParams(data.readInt(), data.readInt(), data.readInt())
				val salt = data.readBlob()!!
				val sealed = data.readBlob()!!
				AppLockRecord(pin, SecretBox(params, salt, sealed), data.readBlob())
			}
		} catch (e: Exception) {
			throw IOException("Unreadable app lock", e)
		}
	}

	@Synchronized
	internal fun write(record: AppLockRecord) {
		val temp = File(file.parentFile, "${file.name}.part")
		writeDurably(temp) { raw ->
			DataOutputStream(raw).run {
				writeByte(VERSION)
				writeBoolean(record.pin)
				writeInt(record.box.params.memoryKib)
				writeInt(record.box.params.iterations)
				writeInt(record.box.params.parallelism)
				writeBlob(record.box.salt)
				writeBlob(record.box.sealed)
				writeBlob(record.biometric)
				flush()
			}
		}
		check(temp.renameTo(file)) { "Could not store the app lock" }
	}

	@Synchronized
	internal fun delete() {
		file.delete()
		_isOpen.value = true
	}

	/** Takes ownership of [payload]. */
	internal fun open(payload: ByteArray) {
		secrets = AppSecrets(payload)
		_isOpen.value = true
	}

	private fun DataOutputStream.writeBlob(bytes: ByteArray?) {
		if (bytes == null) {
			writeInt(-1)
		} else {
			writeInt(bytes.size)
			write(bytes)
		}
	}

	private fun DataInputStream.readBlob(): ByteArray? {
		val size = readInt()
		return if (size < 0) null else ByteArray(size).also(::readFully)
	}

	private companion object {
		const val FILE_NAME = "app.lock"
		const val VERSION = 1
	}
}
