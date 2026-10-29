package com.entsgra.reader.core.security

import android.annotation.SuppressLint
import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.google.crypto.tink.subtle.Hex
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tink keysets stored in app-private prefs, each encrypted by one AES-GCM master key held in
 * Android Keystore. Keysets are created on first use. With the app lock on, the file keyset
 * lives only inside the lock ([AppLockStore]) and is read from there once it is open.
 */
@Singleton
class KeystoreCryptoPrimitives @Inject constructor(
	@ApplicationContext private val context: Context,
	private val appLock: AppLockStore,
) : CryptoPrimitives {

	override val aead: Aead by lazy {
		keyset(name = AEAD_KEYSET, template = Tink.AEAD_TEMPLATE)
			.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
	}

	@Volatile
	private var files: StreamingAead? = null

	override val streamingAead: StreamingAead
		get() = files ?: synchronized(this) {
			files ?: filesKeyset().getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
				.also { files = it }
		}

	/** The file keyset in clear, for sealing into the app lock. Only while the lock is off. */
	@Synchronized
	internal fun exportFilesKeyset(): ByteArray {
		check(!appLock.isEnabled) { "File keyset is inside the app lock" }
		return TinkProtoKeysetFormat.serializeKeyset(
			keyset(FILES_KEYSET, Tink.FILE_TEMPLATE),
			InsecureSecretKeyAccess.get(),
		)
	}

	/**
	 * Writes [handle] back where [AndroidKeysetManager] reads it: hex in prefs, encrypted by the
	 * master key with empty associated data. Committed synchronously, before the lock goes.
	 */
	@Synchronized
	internal fun restoreFilesKeyset(handle: KeysetHandle) {
		val master = AndroidKeystoreKmsClient().getAead(MASTER_KEY_URI)
		val encrypted = TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, master, ByteArray(0))
		val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
			.putString(prefKey(FILES_KEYSET), Hex.encode(encrypted))
			.commit()
		check(saved) { "Could not store the file keyset" }
	}

	/** Once the app lock holds the file keyset, the Keystore-only copy goes. */
	// The KTX edit {} drops the commit result, and this one must be checked.
	@SuppressLint("UseKtx")
	@Synchronized
	internal fun deleteStoredFilesKeyset() {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		if (prefs.contains(prefKey(FILES_KEYSET))) {
			check(prefs.edit().remove(prefKey(FILES_KEYSET)).commit()) { "Could not delete the file keyset" }
		}
	}

	private fun filesKeyset(): KeysetHandle {
		if (appLock.isEnabled) {
			// Never fall through to the manager here: it would make a new, empty keyset.
			return appLock.secrets?.filesKeyset ?: throw AppLockedException()
		}
		return keyset(FILES_KEYSET, Tink.FILE_TEMPLATE)
	}

	private fun keyset(name: String, template: String): KeysetHandle {
		Tink.register
		return AndroidKeysetManager.Builder()
			.withSharedPref(context, prefKey(name), PREFS)
			.withKeyTemplate(KeyTemplates.get(template))
			.withMasterKeyUri(MASTER_KEY_URI)
			.build()
			.keysetHandle
	}

	private fun prefKey(name: String) = "keyset_$name"

	private companion object {
		const val PREFS = "reader_keysets"
		const val MASTER_KEY_URI = "android-keystore://reader_master_key"
		const val AEAD_KEYSET = "aead"
		const val FILES_KEYSET = "files"
	}
}
