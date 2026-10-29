package com.entsgra.reader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.entsgra.reader.core.data.lock.AppLockState
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.FieldLabel
import com.entsgra.reader.core.designsystem.component.InlineControl
import com.entsgra.reader.core.designsystem.component.ReaderChips
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.component.SectionHeadline
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.LockTimeout
import com.entsgra.reader.core.model.SecretKind
import com.entsgra.reader.feature.lock.ConfirmSecretDialog
import com.entsgra.reader.feature.lock.biometricsAvailable
import com.entsgra.reader.feature.lock.rememberBiometricLauncher
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

/** [onAppLockSetup] opens the lock screens to turn the app lock on, or to change its secret. */
fun NavGraphBuilder.settingsScreen(onAppLockSetup: (change: Boolean) -> Unit) {
	composable<SettingsRoute> {
		val viewModel: SettingsViewModel = hiltViewModel()
		val theme by viewModel.theme.collectAsStateWithLifecycle()
		val timeout by viewModel.lockTimeout.collectAsStateWithLifecycle()
		val blockScreenshots by viewModel.blockScreenshots.collectAsStateWithLifecycle()
		val appLock by viewModel.appLock.collectAsStateWithLifecycle()
		val context = LocalContext.current
		val launcher = rememberBiometricLauncher()
		val scope = rememberCoroutineScope()
		val enrollTitle = stringResource(R.string.settings_app_fingerprint)
		var turningOff by remember { mutableStateOf(false) }
		LifecycleResumeEffect(viewModel) {
			viewModel.refresh()
			onPauseOrDispose {}
		}
		SettingsScreen(
			theme = theme,
			onThemeChange = viewModel::setTheme,
			appLock = appLock,
			biometricsOnDevice = remember(context) { biometricsAvailable(context) },
			onAppLock = { on -> if (on) onAppLockSetup(false) else turningOff = true },
			onChangeSecret = { onAppLockSetup(true) },
			onFingerprint = { on ->
				if (on) {
					scope.launch { launcher.authenticate(viewModel.enrollCipher(), enrollTitle, viewModel::enrolled) }
				} else {
					viewModel.stopBiometric()
				}
			},
			onLockNow = viewModel::lockNow,
			timeout = timeout,
			onTimeoutChange = viewModel::setLockTimeout,
			blockScreenshots = blockScreenshots,
			onBlockScreenshotsChange = viewModel::setBlockScreenshots,
		)
		if (turningOff) {
			ConfirmSecretDialog(
				title = stringResource(R.string.settings_app_lock_off_title),
				body = stringResource(R.string.settings_app_lock_off_body),
				kind = appLock?.kind ?: SecretKind.PASSPHRASE,
				confirmLabel = stringResource(R.string.settings_app_lock_off_confirm),
				onConfirm = viewModel::disableAppLock,
				onDismiss = { turningOff = false },
			)
		}
	}
}

@Composable
internal fun SettingsScreen(
	theme: AppTheme?,
	onThemeChange: (AppTheme) -> Unit,
	appLock: AppLockState?,
	biometricsOnDevice: Boolean,
	onAppLock: (Boolean) -> Unit,
	onChangeSecret: () -> Unit,
	onFingerprint: (Boolean) -> Unit,
	onLockNow: () -> Unit,
	timeout: LockTimeout?,
	onTimeoutChange: (LockTimeout) -> Unit,
	blockScreenshots: Boolean,
	onBlockScreenshotsChange: (Boolean) -> Unit,
) {
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4)
			.padding(bottom = ReaderSpacing.space8),
	) {
		ScreenTitle(stringResource(R.string.settings_title))
		SectionHeadline(
			text = stringResource(R.string.settings_theme),
			modifier = Modifier.padding(bottom = ReaderSpacing.space3),
		)
		if (theme != null) {
			val labels = mapOf(
				AppTheme.LIGHT to stringResource(R.string.settings_theme_light),
				AppTheme.DARK to stringResource(R.string.settings_theme_dark),
				AppTheme.YELLOW to stringResource(R.string.settings_theme_yellow),
				AppTheme.PINK to stringResource(R.string.settings_theme_pink),
			)
			ReaderChips(
				options = AppTheme.entries,
				selected = theme,
				onSelect = onThemeChange,
				label = { labels.getValue(it) },
			)
		}
		SectionHeadline(
			text = stringResource(R.string.settings_locks),
			modifier = Modifier.padding(top = ReaderSpacing.space8, bottom = ReaderSpacing.space3),
		)
		Column(verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3)) {
			if (appLock != null) {
				InlineControl(
					label = stringResource(R.string.settings_app_lock),
					checked = appLock.enabled,
					onCheckedChange = onAppLock,
				)
				if (appLock.enabled) {
					ReaderRow(title = stringResource(R.string.settings_change_secret).uppercase(), onClick = onChangeSecret)
					if (biometricsOnDevice) {
						InlineControl(
							label = stringResource(R.string.settings_app_fingerprint),
							checked = appLock.biometric,
							onCheckedChange = onFingerprint,
						)
					}
					SecondaryButton(stringResource(R.string.settings_lock_now), onClick = onLockNow, modifier = Modifier.fillMaxWidth())
				}
			}
			FieldLabel(stringResource(R.string.settings_lock_after), Modifier.padding(top = ReaderSpacing.space3))
			if (timeout != null) {
				val labels = mapOf(
					LockTimeout.IMMEDIATELY to stringResource(R.string.settings_lock_immediately),
					LockTimeout.ONE_MINUTE to stringResource(R.string.settings_lock_one),
					LockTimeout.FIVE_MINUTES to stringResource(R.string.settings_lock_five),
					LockTimeout.FIFTEEN_MINUTES to stringResource(R.string.settings_lock_fifteen),
				)
				ReaderChips(
					options = LockTimeout.entries,
					selected = timeout,
					onSelect = onTimeoutChange,
					label = { labels.getValue(it) },
				)
			}
			BodyText(stringResource(R.string.settings_lock_after_note))
			InlineControl(
				label = stringResource(R.string.settings_block_screenshots),
				checked = blockScreenshots,
				onCheckedChange = onBlockScreenshotsChange,
			)
			BodyText(stringResource(R.string.settings_block_screenshots_note))
		}
	}
}
