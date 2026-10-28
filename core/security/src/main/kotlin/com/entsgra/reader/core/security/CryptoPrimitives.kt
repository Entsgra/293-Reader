package com.entsgra.reader.core.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.StreamingAead

/**
 * Tink primitives backed by keys that never leave Android Keystore in plaintext.
 *
 * Getting them touches Keystore and disk; call from a background dispatcher only.
 */
interface CryptoPrimitives {
	/** Wraps small secrets such as the database passphrase. */
	val aead: Aead

	/** Encrypts book files. Seekable on read, so readers can jump to any page. */
	val streamingAead: StreamingAead
}
