package com.entsgra.reader.core.security

import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretWrapTest {
	private val device = FakeDeviceKeys()
	private val context = "293-reader/vault/v1/folder_7".toByteArray()
	private val payload = ByteArray(200) { it.toByte() }
	private val secret = "472913".toByteArray()

	private fun seal() = SecretWrap.seal(payload, secret, device.aead("folder"), context, TEST_PARAMS)

	@Test
	fun opensWithTheSecretOnThisDevice() {
		val box = seal()
		assertThat(SecretWrap.open(box, secret, device.aead("folder"), context)).isEqualTo(payload)
		assertThat(box.salt).hasLength(16)
		assertThat(box.params).isEqualTo(TEST_PARAMS)
	}

	@Test
	fun wrongSecretIsReportedAsSuch() {
		val box = seal()
		assertThrows(WrongSecretException::class.java) {
			SecretWrap.open(box, "472914".toByteArray(), device.aead("folder"), context)
		}
	}

	@Test
	fun blobAloneCannotBeOpenedWithoutTheKeystoreKey() {
		val box = seal()
		// Another device, or the blob copied off this one: right secret, no Keystore key.
		val elsewhere = FakeDeviceKeys().aead("folder")
		val error = assertThrows(GeneralSecurityException::class.java) {
			SecretWrap.open(box, secret, elsewhere, context)
		}
		assertThat(error).isNotInstanceOf(WrongSecretException::class.java)
	}

	@Test
	fun strippingTheKeystoreLayerStillLeavesTheSecret() {
		val box = seal()
		// A dump taken with the app's Keystore access: the outer layer comes off...
		val inner = device.aead("folder").decrypt(box.sealed, context)
		assertThat(inner.asList().windowed(16).contains(payload.take(16))).isFalse()
		// ...but without the secret the Argon2id layer holds.
		assertThrows(WrongSecretException::class.java) {
			SecretWrap.openInner(inner, "000000".toByteArray(), box.salt, box.params, context)
		}
		assertThat(SecretWrap.openInner(inner, secret, box.salt, box.params, context)).isEqualTo(payload)
	}

	@Test
	fun boxIsBoundToWhatItProtects() {
		val box = seal()
		assertThrows(GeneralSecurityException::class.java) {
			SecretWrap.open(box, secret, device.aead("folder"), "293-reader/vault/v1/folder_8".toByteArray())
		}
	}

	@Test
	fun loweredCostInStorageBreaksTheBox() {
		val box = SecretWrap.seal(payload, secret, device.aead("folder"), context, TEST_PARAMS.copy(iterations = 3))
		val tampered = SecretBox(TEST_PARAMS, box.salt, box.sealed)
		assertThrows(WrongSecretException::class.java) {
			SecretWrap.open(tampered, secret, device.aead("folder"), context)
		}
		val belowFloor = SecretBox(KdfParams(1024, 1, 1), box.salt, box.sealed)
		assertThrows(GeneralSecurityException::class.java) {
			SecretWrap.open(belowFloor, secret, device.aead("folder"), context)
		}
	}

	@Test
	fun weakParamsAreRefusedWhenSealing() {
		assertThrows(IllegalArgumentException::class.java) {
			SecretWrap.seal(payload, secret, device.aead("folder"), context, KdfParams(1024, 1, 1))
		}
	}
}
