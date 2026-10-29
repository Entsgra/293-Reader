package com.entsgra.reader

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.entsgra.reader.core.data.library.BookImporter
import com.entsgra.reader.core.data.lock.AutoLock
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Feeds [AutoLock] the app's trips to the background and the screen turning off, and carries
 * out the app lock: when it falls due, the book being imported (if any) is let finish, then
 * the task is dropped and the process ends, taking every key and decrypted page in memory with
 * it. In the foreground the app restarts straight into the unlock screen.
 */
@Singleton
class AppLockGuard @Inject constructor(
	private val autoLock: AutoLock,
	private val importer: BookImporter,
) {
	private var activity = WeakReference<Activity>(null)
	private var started = 0

	private val _ending = MutableStateFlow(false)

	/** True while waiting for an import to finish before the process ends. */
	val ending: StateFlow<Boolean> = _ending.asStateFlow()

	fun install(application: Application, scope: CoroutineScope) {
		ProcessLifecycleOwner.get().lifecycle.addObserver(
			object : DefaultLifecycleObserver {
				override fun onStart(owner: LifecycleOwner) = autoLock.onForeground()

				override fun onStop(owner: LifecycleOwner) = autoLock.onBackground()
			},
		)
		ContextCompat.registerReceiver(
			application,
			object : BroadcastReceiver() {
				override fun onReceive(context: Context, intent: Intent) = autoLock.onScreenOff()
			},
			IntentFilter(Intent.ACTION_SCREEN_OFF),
			ContextCompat.RECEIVER_NOT_EXPORTED,
		)
		application.registerActivityLifecycleCallbacks(Tracker())
		scope.launch {
			autoLock.appLockDue.first { it }
			_ending.value = true
			importer.stopAfterCurrent()
			endProcess()
		}
	}

	private fun endProcess() {
		val current = activity.get()
		if (current != null && started > 0) {
			// Straight back in, at the unlock screen, in a fresh process.
			current.startActivity(Intent.makeRestartActivityTask(current.componentName))
		} else {
			// Drops the task's saved state along with it.
			current?.finishAndRemoveTask()
		}
		Process.killProcess(Process.myPid())
	}

	private inner class Tracker : Application.ActivityLifecycleCallbacks {
		override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
			this@AppLockGuard.activity = WeakReference(activity)
		}

		override fun onActivityStarted(activity: Activity) {
			started++
		}

		override fun onActivityStopped(activity: Activity) {
			started--
		}

		override fun onActivityResumed(activity: Activity) = Unit

		override fun onActivityPaused(activity: Activity) = Unit

		override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

		override fun onActivityDestroyed(activity: Activity) = Unit
	}
}
