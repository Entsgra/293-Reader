package com.entsgra.reader.core.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A file, cover or field under a locked folder's key, read while that folder is locked. */
class FolderLockedException(val folderId: Long) : IllegalStateException("Folder $folderId is locked")

/**
 * A locked folder's keys while it is unlocked: a StreamingAead keyset (the app's file template)
 * for books and covers, and an Aead keyset for titles, authors and folder names.
 */
class FolderKeys internal constructor(
	/** Both keysets serialized; kept to re-wrap for biometrics. Zeroed on lock. */
	internal val payload: ByteArray,
	val files: StreamingAead,
	private val metadata: Aead,
) {
	fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray = metadata.encrypt(plaintext, associatedData)

	fun open(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = metadata.decrypt(ciphertext, associatedData)

	internal fun destroy() = payload.fill(0)

	internal companion object {
		private const val VERSION = 1

		fun generate(): ByteArray {
			Tink.register
			val files = KeysetHandle.generateNew(KeyTemplates.get(Tink.FILE_TEMPLATE))
			val metadata = KeysetHandle.generateNew(KeyTemplates.get(Tink.AEAD_TEMPLATE))
			val out = ByteArrayOutputStream()
			DataOutputStream(out).use { data ->
				data.writeByte(VERSION)
				listOf(files, metadata).forEach {
					val bytes = TinkProtoKeysetFormat.serializeKeyset(it, InsecureSecretKeyAccess.get())
					data.writeInt(bytes.size)
					data.write(bytes)
					bytes.fill(0)
				}
			}
			return out.toByteArray()
		}

		/** Takes ownership of [payload]. */
		fun load(payload: ByteArray): FolderKeys {
			Tink.register
			val data = DataInputStream(payload.inputStream())
			if (data.readUnsignedByte() != VERSION) throw GeneralSecurityException("Unknown folder key version")
			val handles = List(2) {
				val bytes = ByteArray(data.readInt()).also(data::readFully)
				TinkProtoKeysetFormat.parseKeyset(bytes, InsecureSecretKeyAccess.get()).also { bytes.fill(0) }
			}
			return FolderKeys(
				payload = payload,
				files = handles[0].getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java),
				metadata = handles[1].getPrimitive(RegistryConfiguration.get(), Aead::class.java),
			)
		}
	}
}

/**
 * Keys of the folders unlocked right now, in memory only. Locking drops them: the serialized
 * keysets are zeroed and the Tink objects left to the garbage collector (they cannot be zeroed).
 */
@Singleton
class Keyring @Inject constructor() {
	private val keys = ConcurrentHashMap<Long, FolderKeys>()
	private val _unlocked = MutableStateFlow<Set<Long>>(emptySet())
	val unlocked: StateFlow<Set<Long>> = _unlocked.asStateFlow()

	operator fun get(folderId: Long): FolderKeys? = keys[folderId]

	fun require(folderId: Long): FolderKeys = keys[folderId] ?: throw FolderLockedException(folderId)

	internal fun put(folderId: Long, folderKeys: FolderKeys) {
		keys.put(folderId, folderKeys)?.destroy()
		_unlocked.update { it + folderId }
	}

	fun lock(folderId: Long) {
		keys.remove(folderId)?.destroy()
		_unlocked.update { it - folderId }
	}

	fun lockAll() {
		keys.keys.toList().forEach { id -> keys.remove(id)?.destroy() }
		_unlocked.value = emptySet()
	}
}

internal object Tink {
	const val FILE_TEMPLATE = "AES256_GCM_HKDF_1MB"
	const val AEAD_TEMPLATE = "AES256_GCM"

	val register: Unit by lazy {
		AeadConfig.register()
		StreamingAeadConfig.register()
	}
}
