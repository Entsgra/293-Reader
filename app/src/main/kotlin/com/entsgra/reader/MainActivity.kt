package com.entsgra.reader

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.designsystem.theme.applyReaderWindow
import com.entsgra.reader.navigation.ReaderApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	private val viewModel: MainViewModel by viewModels()

	/** Set once the window and the first composition carry the stored theme. */
	private var themeApplied = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		holdFirstDrawUntilThemeLoaded()
		setContent {
			val theme by viewModel.theme.collectAsStateWithLifecycle()
			val current = theme ?: return@setContent
			DisposableEffect(current) {
				applyReaderWindow(current)
				themeApplied = true
				onDispose {}
			}
			ReaderTheme(current) {
				ReaderApp()
			}
		}
	}

	/**
	 * The stored theme is read off the main thread, so cancel draws until it has been
	 * composed and applied to the window. The splash screen (or starting window) stays up
	 * meanwhile, and the first frame drawn is already in the right theme. The read is warmed
	 * in [ReaderApplication], so this usually costs zero or one frame.
	 */
	private fun holdFirstDrawUntilThemeLoaded() {
		val content = findViewById<View>(android.R.id.content)
		content.viewTreeObserver.addOnPreDrawListener(
			object : ViewTreeObserver.OnPreDrawListener {
				override fun onPreDraw(): Boolean {
					if (!themeApplied) return false
					content.viewTreeObserver.removeOnPreDrawListener(this)
					return true
				}
			},
		)
	}
}
