package com.entsgra.reader.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.text.Normalizer
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id cost. Stored next to every wrap, so the defaults can rise later without breaking
 * locks made before.
 */
data class KdfParams(val memoryKib: Int, val iterations: Int, val parallelism: Int) {
	/** At least OWASP's minimum for Argon2id: 19 MiB and two passes. */
	val isStrongEnough: Boolean
		get() = memoryKib >= FLOOR.memoryKib && iterations >= FLOOR.iterations && parallelism >= 1

	companion object {
		/** About a second on a mid-range phone; 64 MiB stays well inside the app's heap. */
		val DEFAULT = KdfParams(memoryKib = 64 * 1024, iterations = 3, parallelism = 1)
		val FLOOR = KdfParams(memoryKib = 19 * 1024, iterations = 2, parallelism = 1)
	}
}

internal object Argon2id {
	const val SALT_BYTES = 16
	const val KEY_BYTES = 32

	/** Blocking and memory-hard by design; call from a background dispatcher. */
	fun derive(
		password: ByteArray,
		salt: ByteArray,
		params: KdfParams,
		outBytes: Int = KEY_BYTES,
		secret: ByteArray? = null,
		additional: ByteArray? = null,
	): ByteArray {
		val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
			.withVersion(Argon2Parameters.ARGON2_VERSION_13)
			.withMemoryAsKB(params.memoryKib)
			.withIterations(params.iterations)
			.withParallelism(params.parallelism)
			.withSalt(salt)
			.apply {
				if (secret != null) withSecret(secret)
				if (additional != null) withAdditional(additional)
			}
			.build()
		val generator = Argon2BytesGenerator().apply { init(parameters) }
		return ByteArray(outBytes).also { generator.generateBytes(password, it) }
	}
}

/**
 * UTF-8 of [secret] in Unicode NFC, so a passphrase typed on another keyboard derives the same
 * key. The caller zeroes the result.
 */
internal fun secretBytes(secret: CharArray): ByteArray {
	val normalized = Normalizer.normalize(CharBuffer.wrap(secret), Normalizer.Form.NFC)
	val encoded = Charsets.UTF_8.encode(normalized)
	return ByteArray(encoded.remaining()).also { encoded.get(it) }.also { clear(encoded) }
}

private fun clear(buffer: ByteBuffer) {
	if (buffer.hasArray()) buffer.array().fill(0)
}
