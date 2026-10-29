package com.entsgra.reader

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.component.LiveStatus
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.designsystem.theme.SecureFlag
import com.entsgra.reader.core.designsystem.theme.applyReaderWindow
import com.entsgra.reader.core.model.SecretKind
import com.entsgra.reader.feature.lock.UnlockGate
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
			val appOpen by viewModel.appOpen.collectAsStateWithLifecycle()
			val appLock by viewModel.appLock.collectAsStateWithLifecycle()
			val blockScreenshots by viewModel.blockScreenshots.collectAsStateWithLifecycle()
			val lockingAfterImport by viewModel.lockingAfterImport.collectAsStateWithLifecycle()
			val current = theme ?: return@setContent
			DisposableEffect(current) {
				applyReaderWindow(current)
				themeApplied = true
				onDispose {}
			}
			DisposableEffect(blockScreenshots) {
				if (blockScreenshots) SecureFlag.acquire(window)
				onDispose { if (blockScreenshots) SecureFlag.release(window) }
			}
			val lockEnabled = appLock?.enabled == true
			DisposableEffect(lockEnabled) {
				// With the app lock on, the recents preview must not show what was on screen.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!lockEnabled)
				onDispose {}
			}
			ReaderTheme(current) {
				when {
					appOpen -> ReaderApp()
					appLock != null -> Box(Modifier.fillMaxSize().background(ReaderTheme.colors.fog)) {
						UnlockGate(
							title = stringResource(R.string.app_name),
							kind = appLock?.kind ?: SecretKind.PASSPHRASE,
							controller = viewModel.unlock,
							modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
						)
					}
					else -> Box(Modifier.fillMaxSize().background(ReaderTheme.colors.fog))
				}
				if (lockingAfterImport) {
					Box(
						Modifier
							.fillMaxSize()
							.background(ReaderTheme.colors.fog),
						contentAlignment = Alignment.Center,
					) {
						LiveStatus(stringResource(R.string.locking_after_import), Modifier.padding(ReaderSpacing.space4))
					}
				}
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
