package com.entsgra.reader.feature.lock

import com.entsgra.reader.core.data.lock.BiometricStart
import com.entsgra.reader.core.data.lock.UnlockResult
import com.google.common.truth.Truth.assertThat
import javax.crypto.Cipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockControllerTest {
	private class FakeOps(var result: UnlockResult = UnlockResult.Ok, var start: BiometricStart = BiometricStart.None) : UnlockOps {
		var secrets = mutableListOf<String>()

		override suspend fun unlock(secret: CharArray) = result.also { secrets += String(secret) }
		override suspend fun waitMs() = 0L
		override suspend fun hasBiometric() = start != BiometricStart.None
		override suspend fun biometricStart() = start
		override suspend fun biometricUnlock(cipher: Cipher) = Unit
		override suspend fun biometricEnrollCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")
		override suspend fun biometricEnroll(cipher: Cipher) = Unit
	}

	@Test
	fun wrongSecretShowsAndCountsDownTheWait() = runTest {
		val ops = FakeOps(result = UnlockResult.Wrong(waitMs = 30_000))
		val controller = UnlockController(backgroundScope, ops)
		runCurrent()

		controller.submit("000000")
		runCurrent()
		assertThat(controller.state.value.wrong).isTrue()
		assertThat(controller.state.value.waitSeconds).isEqualTo(30)

		// No attempts while the wait runs.
		controller.submit("472913")
		runCurrent()
		assertThat(ops.secrets).containsExactly("000000")

		advanceTimeBy(29_001)
		assertThat(controller.state.value.waitSeconds).isEqualTo(1)
		advanceTimeBy(1_000)
		assertThat(controller.state.value.waitSeconds).isEqualTo(0)
	}

	@Test
	fun invalidatedBiometricsLeadToAnOfferAfterThePin() = runTest {
		val ops = FakeOps(start = BiometricStart.Invalidated)
		val controller = UnlockController(backgroundScope, ops)
		runCurrent()
		assertThat(controller.state.value.biometric).isTrue()

		assertThat(controller.biometricCipher()).isNull()
		assertThat(controller.state.value.invalidated).isTrue()
		assertThat(controller.state.value.biometric).isFalse()

		controller.submit("472913")
		runCurrent()
		assertThat(controller.state.value.offerBiometric).isTrue()
		controller.declineBiometric()
		assertThat(controller.state.value.offerBiometric).isFalse()
	}

	@Test
	fun waitsFormatAsClockTime() {
		assertThat(formatWait(28)).isEqualTo("0:28")
		assertThat(formatWait(240)).isEqualTo("4:00")
		assertThat(formatWait(3600)).isEqualTo("1:00:00")
	}
}
