package com.entsgra.reader.core.security

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UnlockThrottleTest {
	@get:Rule val tmp = TemporaryFolder()

	private val clock = FakeClock()
	private fun throttle() = UnlockThrottle(File(tmp.root, "attempts"), clock)

	@Test
	fun scheduleIsFourFreeThenThirtySecondsDoublingToAnHour() {
		assertThat((0..4).map(::lockoutMs)).containsExactly(0L, 0L, 0L, 0L, 0L).inOrder()
		assertThat((5..12).map(::lockoutMs)).containsExactly(
			30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 960_000L, 1_920_000L, 3_600_000L,
		).inOrder()
		assertThat(lockoutMs(100)).isEqualTo(3_600_000L)
	}

	@Test
	fun fifthFailureStartsAWaitThatRunsOut() {
		val throttle = throttle()
		repeat(4) { assertThat(throttle.failed("folder_1")).isEqualTo(0) }
		assertThat(throttle.waitMs("folder_1")).isEqualTo(0)
		assertThat(throttle.failed("folder_1")).isEqualTo(30_000)
		assertThat(throttle.waitMs("folder_1")).isEqualTo(30_000)
		clock.advance(20_000)
		assertThat(throttle.waitMs("folder_1")).isEqualTo(10_000)
		clock.advance(10_000)
		assertThat(throttle.waitMs("folder_1")).isEqualTo(0)
		// Other secrets keep their own count.
		assertThat(throttle.waitMs("app")).isEqualTo(0)
	}

	@Test
	fun countSurvivesARestartAndSuccessResetsIt() {
		repeat(5) { throttle().failed("app") }
		assertThat(throttle().waitMs("app")).isEqualTo(30_000)
		throttle().succeeded("app")
		assertThat(throttle().waitMs("app")).isEqualTo(0)
		assertThat(throttle().failed("app")).isEqualTo(0)
	}

	@Test
	fun settingTheClockForwardDoesNotSkipTheWait() {
		val throttle = throttle()
		repeat(5) { throttle.failed("app") }
		clock.wall += 24 * 60 * 60_000L
		assertThat(throttle.waitMs("app")).isEqualTo(30_000)
	}

	@Test
	fun settingTheClockBackCannotStretchTheWait() {
		val throttle = throttle()
		repeat(5) { throttle.failed("app") }
		clock.wall -= 365 * 24 * 60 * 60_000L
		clock.elapsed += 30_000
		assertThat(throttle.waitMs("app")).isEqualTo(30_000)
	}

	@Test
	fun afterARebootTheWallClockHoldsTheWait() {
		val throttle = throttle()
		repeat(5) { throttle.failed("app") }
		clock.boot += 1
		clock.elapsed = 5_000
		clock.wall += 10_000
		assertThat(throttle.waitMs("app")).isEqualTo(20_000)
	}
}
