package com.entsgra.reader.core.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDisplay

/**
 * The README device for Robolectric `@Config(qualifiers = ...)`: a 1080 x 2400 phone at 420 dpi
 * (411 x 914 dp), portrait, US English.
 */
const val SCREENSHOT_QUALIFIERS = "en-rUS-w411dp-h914dp-normal-long-notround-port-420dpi-keyshidden-nonav"

const val SCREENSHOT_SDK = 37

private const val SCREEN_WIDTH_PX = 1080
private const val SCREEN_HEIGHT_PX = 2400

/**
 * A compose rule on the README device. 411 x 914 dp at 420 dpi rounds down to 1078 x 2399, so
 * the display is pinned to the exact pixels before the activity starts.
 */
fun screenshotRule(): ComposeContentTestRule {
	val compose = createComposeRule()
	return object : ComposeContentTestRule by compose {
		override fun apply(base: Statement, description: Description): Statement {
			val inner = compose.apply(base, description)
			return object : Statement() {
				override fun evaluate() {
					Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).apply {
						setWidth(SCREEN_WIDTH_PX)
						setHeight(SCREEN_HEIGHT_PX)
						setRealWidth(SCREEN_WIDTH_PX)
						setRealHeight(SCREEN_HEIGHT_PX)
					}
					inner.evaluate()
				}
			}
		}
	}
}

/** Shows [content] full screen in [theme], on the theme's ground as the app does. */
fun ComposeContentTestRule.setScreen(theme: AppTheme = AppTheme.LIGHT, content: @Composable () -> Unit) {
	setContent {
		ReaderTheme(theme) {
			Box(
				Modifier
					.fillMaxSize()
					.background(ReaderTheme.colors.fog),
			) { content() }
		}
	}
}

/** Records the screen as `docs/screenshots/[name].png`. */
fun ComposeContentTestRule.captureScreen(name: String) {
	waitForIdle()
	onRoot().captureRoboImage("$name.png")
}
