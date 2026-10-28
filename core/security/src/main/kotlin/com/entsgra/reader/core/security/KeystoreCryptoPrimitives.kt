package com.entsgra.reader.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tink keysets stored in app-private prefs, each encrypted by one AES-GCM master key held in
 * Android Keystore. Keysets are created on first use.
 */
@Singleton
class KeystoreCryptoPrimitives @Inject constructor(
	@ApplicationContext private val context: Context,
) : CryptoPrimitives {

	override val aead: Aead by lazy {
		keyset(name = "aead", template = "AES256_GCM")
			.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
	}

	override val streamingAead: StreamingAead by lazy {
		keyset(name = "files", template = "AES256_GCM_HKDF_1MB")
			.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
	}

	private fun keyset(name: String, template: String): KeysetHandle {
		register
		return AndroidKeysetManager.Builder()
			.withSharedPref(context, "keyset_$name", PREFS)
			.withKeyTemplate(KeyTemplates.get(template))
			.withMasterKeyUri(MASTER_KEY_URI)
			.build()
			.keysetHandle
	}

	private companion object {
		const val PREFS = "reader_keysets"
		const val MASTER_KEY_URI = "android-keystore://reader_master_key"

		val register: Unit by lazy {
			AeadConfig.register()
			StreamingAeadConfig.register()
		}
	}
}
