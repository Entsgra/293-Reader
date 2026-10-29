package com.entsgra.reader.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.SecretKind

@Composable
private fun Themed(theme: AppTheme, content: @Composable () -> Unit) {
	ReaderTheme(theme) {
		Box(Modifier.background(ReaderTheme.colors.fog)) { content() }
	}
}

@Composable
private fun Unlock(theme: AppTheme) = Themed(theme) {
	UnlockContent(
		title = "Private",
		kind = SecretKind.PIN,
		state = UnlockUiState(wrong = true, waitSeconds = 28, biometric = true, invalidated = true),
		biometricsOnDevice = true,
		onUnlock = {},
		onBiometric = {},
		onBack = {},
	)
}

@Composable
private fun Setup(theme: AppTheme) = Themed(theme) {
	LockSetupContent(
		title = "Lock Private",
		forFolder = true,
		change = false,
		currentKind = null,
		state = SetupState.Editing(),
		biometricsOnDevice = true,
		onSubmit = { _, _, _ -> },
		onBiometric = {},
		onBack = {},
	)
}

@Composable
private fun Done(theme: AppTheme) = Themed(theme) {
	LockSetupContent(
		title = "App lock",
		forFolder = false,
		change = false,
		currentKind = null,
		state = SetupState.Done,
		biometricsOnDevice = true,
		onSubmit = { _, _, _ -> },
		onBiometric = {},
		onBack = {},
	)
}

@Preview(name = "Unlock light", widthDp = 360, heightDp = 800)
@Composable
private fun UnlockLight() = Unlock(AppTheme.LIGHT)

@Preview(name = "Unlock dark", widthDp = 360, heightDp = 800)
@Composable
private fun UnlockDark() = Unlock(AppTheme.DARK)

@Preview(name = "Unlock yellow", widthDp = 360, heightDp = 800)
@Composable
private fun UnlockYellow() = Unlock(AppTheme.YELLOW)

@Preview(name = "Unlock pink", widthDp = 360, heightDp = 800)
@Composable
private fun UnlockPink() = Unlock(AppTheme.PINK)

@Preview(name = "Setup light", widthDp = 360, heightDp = 1000)
@Composable
private fun SetupLight() = Setup(AppTheme.LIGHT)

@Preview(name = "Setup dark", widthDp = 360, heightDp = 1000)
@Composable
private fun SetupDark() = Setup(AppTheme.DARK)

@Preview(name = "Setup yellow", widthDp = 360, heightDp = 1000)
@Composable
private fun SetupYellow() = Setup(AppTheme.YELLOW)

@Preview(name = "Setup pink", widthDp = 360, heightDp = 1000)
@Composable
private fun SetupPink() = Setup(AppTheme.PINK)

@Preview(name = "Done light", widthDp = 360, heightDp = 600)
@Composable
private fun DoneLight() = Done(AppTheme.LIGHT)

@Preview(name = "Done dark", widthDp = 360, heightDp = 600)
@Composable
private fun DoneDark() = Done(AppTheme.DARK)

@Preview(name = "Done yellow", widthDp = 360, heightDp = 600)
@Composable
private fun DoneYellow() = Done(AppTheme.YELLOW)

@Preview(name = "Done pink", widthDp = 360, heightDp = 600)
@Composable
private fun DonePink() = Done(AppTheme.PINK)
