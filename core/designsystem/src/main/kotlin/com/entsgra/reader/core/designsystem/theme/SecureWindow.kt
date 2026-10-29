package com.entsgra.reader.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Keeps screenshots, screen recording and the recents preview off the window while this is in
 * the composition: on every screen that shows locked-folder content or asks for a secret.
 * Dialogs opened meanwhile inherit the flag.
 */
@Composable
fun SecureWindow() {
	val context = LocalContext.current
	DisposableEffect(context) {
		val window = context.findActivity()?.window
		if (window != null) SecureFlag.acquire(window)
		onDispose { if (window != null) SecureFlag.release(window) }
	}
}

/**
 * FLAG_SECURE counted per window, so one screen leaving does not clear the flag another screen
 * (or the app-wide setting) still needs. Main thread only.
 */
object SecureFlag {
	private val holds = WeakHashMap<Window, Int>()

	fun acquire(window: Window) {
		val count = holds[window] ?: 0
		holds[window] = count + 1
		if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
	}

	fun release(window: Window) {
		val count = holds[window] ?: return
		if (count <= 1) {
			holds.remove(window)
			window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
		} else {
			holds[window] = count - 1
		}
	}
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivity()
	else -> null
}
