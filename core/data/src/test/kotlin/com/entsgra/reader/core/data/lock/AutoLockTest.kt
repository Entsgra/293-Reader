package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.model.LockTimeout
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockTest {
	private var folderLocks = 0
	private var appLockOpen = false
	private val timeout = MutableStateFlow(LockTimeout.ONE_MINUTE)

	private fun TestScope.autoLock(clock: () -> Long = { currentTime }) = AutoLock(
		lockFolders = { folderLocks++ },
		appLockOpen = { appLockOpen },
		timeout = timeout,
		scope = backgroundScope,
		now = clock,
	)

	@Test
	fun backgroundLocksFoldersAfterTheTimeout() = runTest {
		val lock = autoLock()
		lock.onBackground()
		advanceTimeBy(59_999)
		assertThat(folderLocks).isEqualTo(0)
		advanceTimeBy(2)
		assertThat(folderLocks).isEqualTo(1)
	}

	@Test
	fun comingBackInTimeCancelsTheLock() = runTest {
		val lock = autoLock()
		lock.onBackground()
		advanceTimeBy(30_000)
		lock.onForeground()
		advanceTimeBy(120_000)
		assertThat(folderLocks).isEqualTo(0)
	}

	@Test
	fun immediatelyLocksOnTheWayOut() = runTest {
		timeout.value = LockTimeout.IMMEDIATELY
		autoLock().onBackground()
		assertThat(folderLocks).isEqualTo(1)
	}

	@Test
	fun aFrozenTimerIsCaughtWhenTheAppReturns() = runTest {
		// The process was frozen: the clock moves on but the timer never runs.
		var clock = 0L
		val lock = autoLock { clock }
		lock.onBackground()
		clock = 5 * 60_000L
		lock.onForeground()
		assertThat(folderLocks).isEqualTo(1)
	}

	@Test
	fun screenOffLocksFoldersButNotTheApp() = runTest {
		appLockOpen = true
		val lock = autoLock()
		lock.onScreenOff()
		assertThat(folderLocks).isEqualTo(1)
		assertThat(lock.appLockDue.value).isFalse()
	}

	@Test
	fun theAppLockFollowsTheTimeout() = runTest {
		appLockOpen = true
		val lock = autoLock()
		lock.onBackground()
		advanceTimeBy(60_001)
		assertThat(lock.appLockDue.value).isTrue()
	}

	@Test
	fun withoutAnAppLockNothingAsksToEndTheProcess() = runTest {
		val lock = autoLock()
		lock.lockNow()
		assertThat(folderLocks).isEqualTo(1)
		assertThat(lock.appLockDue.value).isFalse()
	}

	@Test
	fun thePickerTripDoesNotCount() = runTest {
		timeout.value = LockTimeout.IMMEDIATELY
		val lock = autoLock()
		lock.holdForPicker()
		lock.onBackground()
		runCurrent()
		assertThat(folderLocks).isEqualTo(0)
		lock.onForeground()
		lock.onBackground()
		assertThat(folderLocks).isEqualTo(1)
	}
}
