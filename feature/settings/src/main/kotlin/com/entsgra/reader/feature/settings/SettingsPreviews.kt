package com.entsgra.reader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.entsgra.reader.core.data.lock.AppLockState
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.LockTimeout
import com.entsgra.reader.core.model.SecretKind

@Composable
private fun Settings(theme: AppTheme) = ReaderTheme(theme) {
	Box(Modifier.background(ReaderTheme.colors.fog)) {
		SettingsScreen(
			theme = theme,
			onThemeChange = {},
			appLock = AppLockState(enabled = true, kind = SecretKind.PASSPHRASE, biometric = false),
			biometricsOnDevice = true,
			onAppLock = {},
			onChangeSecret = {},
			onFingerprint = {},
			onLockNow = {},
			timeout = LockTimeout.ONE_MINUTE,
			onTimeoutChange = {},
			blockScreenshots = false,
			onBlockScreenshotsChange = {},
		)
	}
}

@Preview(name = "Settings light", widthDp = 360, heightDp = 1100)
@Composable
private fun SettingsLight() = Settings(AppTheme.LIGHT)

@Preview(name = "Settings dark", widthDp = 360, heightDp = 1100)
@Composable
private fun SettingsDark() = Settings(AppTheme.DARK)

@Preview(name = "Settings yellow", widthDp = 360, heightDp = 1100)
@Composable
private fun SettingsYellow() = Settings(AppTheme.YELLOW)

@Preview(name = "Settings pink", widthDp = 360, heightDp = 1100)
@Composable
private fun SettingsPink() = Settings(AppTheme.PINK)
