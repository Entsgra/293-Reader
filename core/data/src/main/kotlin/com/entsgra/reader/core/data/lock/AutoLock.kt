package com.entsgra.reader.core.data.lock

import android.os.SystemClock
import com.entsgra.reader.core.data.ApplicationScope
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.model.LockTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * When locks close on their own. Locked folders close on screen off, and once the app has been
 * in the background for the timeout. The app lock follows the timeout only; when it falls due,
 * [appLockDue] turns true and the app module ends the process.
 *
 * The timer runs in the background, but Android may freeze a background app before it fires,
 * so the elapsed time is checked again when the app comes back, before anything is shown.
 */
@Singleton
class AutoLock internal constructor(
	private val lockFolders: () -> Unit,
	private val appLockOpen: () -> Boolean,
	private val timeout: StateFlow<LockTimeout>,
	private val scope: CoroutineScope,
	private val now: () -> Long,
) {
	@Inject
	constructor(
		folders: FolderLocks,
		appLocks: AppLocks,
		settings: SettingsRepository,
		@ApplicationScope scope: CoroutineScope,
	) : this(
		lockFolders = folders::lockAll,
		appLockOpen = { appLocks.opened },
		timeout = settings.lockTimeout.stateIn(scope, SharingStarted.Eagerly, LockTimeout.ONE_MINUTE),
		scope = scope,
		now = SystemClock::elapsedRealtime,
	)

	private var backgroundedAt: Long? = null
	private var timer: Job? = null
	private var holding = false

	private val _appLockDue = MutableStateFlow(false)
	val appLockDue: StateFlow<Boolean> = _appLockDue.asStateFlow()

	@Synchronized
	fun onBackground() {
		if (holding) return
		backgroundedAt = now()
		val wait = timeout.value.millis
		if (wait == 0L) {
			fire()
		} else {
			timer?.cancel()
			timer = scope.launch {
				delay(wait)
				fire()
			}
		}
	}

	@Synchronized
	fun onForeground() {
		timer?.cancel()
		timer = null
		holding = false
		val due = lockDue(timeout.value, backgroundedAt, now())
		backgroundedAt = null
		if (due) fire()
	}

	/** Locked folders close whenever the screen goes off. The app lock does not. */
	fun onScreenOff() = lockFolders()

	/**
	 * The app is about to hand the screen to another app the person asked for (the file
	 * picker); the next trip to the background does not count. Screen off still locks.
	 */
	@Synchronized
	fun holdForPicker() {
		holding = true
	}

	/** Locks every folder and, when the app lock is on, asks for the process to end. */
	@Synchronized
	fun lockNow() = fire()

	private fun fire() {
		lockFolders()
		if (appLockOpen()) _appLockDue.value = true
	}
}
