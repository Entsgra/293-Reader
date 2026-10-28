package com.entsgra.reader.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Argon2idTest {
	@Test
	fun matchesTheRfc9106TestVector() {
		// RFC 9106, section 5.3: Argon2id, version 0x13.
		val tag = Argon2id.derive(
			password = ByteArray(32) { 0x01 },
			salt = ByteArray(16) { 0x02 },
			params = KdfParams(memoryKib = 32, iterations = 3, parallelism = 4),
			outBytes = 32,
			secret = ByteArray(8) { 0x03 },
			additional = ByteArray(12) { 0x04 },
		)
		assertThat(tag.toHex()).isEqualTo("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659")
	}

	@Test
	fun defaultsMeetTheFloorAndWeakerParamsDoNot() {
		assertThat(KdfParams.DEFAULT).isEqualTo(KdfParams(memoryKib = 65_536, iterations = 3, parallelism = 1))
		assertThat(KdfParams.DEFAULT.isStrongEnough).isTrue()
		assertThat(KdfParams.FLOOR).isEqualTo(KdfParams(memoryKib = 19_456, iterations = 2, parallelism = 1))
		assertThat(KdfParams.FLOOR.isStrongEnough).isTrue()
		assertThat(KdfParams(memoryKib = 19_455, iterations = 2, parallelism = 1).isStrongEnough).isFalse()
		assertThat(KdfParams(memoryKib = 65_536, iterations = 1, parallelism = 1).isStrongEnough).isFalse()
		assertThat(KdfParams(memoryKib = 65_536, iterations = 3, parallelism = 0).isStrongEnough).isFalse()
	}

	@Test
	fun saltAndCostChangeTheKey() {
		val password = "correct horse".toByteArray()
		val base = Argon2id.derive(password, ByteArray(16), TEST_PARAMS)
		assertThat(Argon2id.derive(password, ByteArray(16), TEST_PARAMS)).isEqualTo(base)
		assertThat(Argon2id.derive(password, ByteArray(16) { 1 }, TEST_PARAMS)).isNotEqualTo(base)
		assertThat(Argon2id.derive(password, ByteArray(16), TEST_PARAMS.copy(iterations = 3))).isNotEqualTo(base)
		assertThat(base).hasLength(32)
	}

	@Test
	fun composedAndDecomposedAccentsGiveTheSameBytes() {
		val composed = secretBytes("café".toCharArray())
		val decomposed = secretBytes("café".toCharArray())
		assertThat(decomposed).isEqualTo(composed)
		assertThat(composed).isEqualTo("café".toByteArray(Charsets.UTF_8))
	}

	private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
