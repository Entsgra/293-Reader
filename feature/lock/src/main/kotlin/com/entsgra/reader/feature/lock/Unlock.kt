package com.entsgra.reader.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.DialogButtons
import com.entsgra.reader.core.designsystem.component.ErrorNotice
import com.entsgra.reader.core.designsystem.component.FieldLabel
import com.entsgra.reader.core.designsystem.component.LiveStatus
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderDialog
import com.entsgra.reader.core.designsystem.component.ReaderTextField
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.SecureWindow
import com.entsgra.reader.core.model.SecretKind
import kotlinx.coroutines.launch

/**
 * The unlock screen for a locked folder or the app, wired to [controller] and the system
 * biometric prompt. Keeps the window secure while shown.
 */
@Composable
fun UnlockGate(
	title: String,
	kind: SecretKind,
	controller: UnlockController,
	modifier: Modifier = Modifier,
	onBack: (() -> Unit)? = null,
) {
	val state by controller.state.collectAsStateWithLifecycle()
	val launcher = rememberBiometricLauncher()
	val scope = rememberCoroutineScope()
	val context = LocalContext.current
	val promptTitle = stringResource(R.string.biometric_prompt_unlock)
	LaunchedEffect(controller) { controller.refresh() }
	UnlockContent(
		title = title,
		kind = kind,
		state = state,
		biometricsOnDevice = remember(context) { biometricsAvailable(context) },
		onUnlock = controller::submit,
		onBiometric = {
			scope.launch {
				controller.biometricCipher()?.let { cipher ->
					launcher.authenticate(cipher, promptTitle, controller::biometricMatched)
				}
			}
		},
		modifier = modifier,
		onBack = onBack,
	)
}

/** After an unlock that followed a fingerprint change: offers to turn fingerprint unlock on again. */
@Composable
fun ReenrollOffer(controller: UnlockController) {
	val state by controller.state.collectAsStateWithLifecycle()
	if (!state.offerBiometric) return
	val launcher = rememberBiometricLauncher()
	val scope = rememberCoroutineScope()
	val promptTitle = stringResource(R.string.biometric_prompt_enroll)
	ReaderDialog(title = stringResource(R.string.unlock_reenroll_title), onDismiss = controller::declineBiometric) {
		BodyText(stringResource(R.string.unlock_reenroll_body))
		DialogButtons {
			PrimaryButton(
				stringResource(R.string.unlock_reenroll_confirm),
				onClick = {
					scope.launch {
						launcher.authenticate(controller.enrollCipher(), promptTitle, controller::enrolled)
					}
				},
				modifier = Modifier.fillMaxWidth(),
			)
			SecondaryButton(stringResource(R.string.not_now), onClick = controller::declineBiometric, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Composable
fun UnlockContent(
	title: String,
	kind: SecretKind,
	state: UnlockUiState,
	biometricsOnDevice: Boolean,
	onUnlock: (String) -> Unit,
	onBiometric: () -> Unit,
	modifier: Modifier = Modifier,
	onBack: (() -> Unit)? = null,
) {
	SecureWindow()
	// Not saveable: a typed secret never goes into saved state.
	var secret by remember { mutableStateOf("") }
	LaunchedEffect(state.wrong, state.waitSeconds > 0) { if (state.wrong) secret = "" }
	val submit = { onUnlock(secret) }
	Column(
		modifier
			.fillMaxSize()
			// The keyboard covers the window edge to edge; keep the fields above it.
			.imePadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4),
		verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space4),
	) {
		ScreenTitle(title, onBack = onBack, backLabel = stringResource(R.string.back))
		SweepKeyCard {
			FieldLabel(stringResource(R.string.unlock_head))
			BodyText(stringResource(if (kind == SecretKind.PIN) R.string.unlock_body_pin else R.string.unlock_body_passphrase))
			if (state.busy) LiveStatus(stringResource(R.string.unlock_checking))
		}
		if (state.invalidated) ErrorNotice(stringResource(R.string.unlock_invalidated))
		ReaderTextField(
			value = secret,
			onValueChange = { secret = it },
			label = kindLabel(kind),
			error = when {
				state.waitSeconds > 0 -> stringResource(R.string.unlock_wait, formatWait(state.waitSeconds))
				state.wrong -> stringResource(if (kind == SecretKind.PIN) R.string.unlock_wrong_pin else R.string.unlock_wrong_passphrase)
				else -> null
			},
			keyboardType = keyboardFor(kind),
			secret = true,
			onImeAction = submit,
		)
		PrimaryButton(
			stringResource(R.string.unlock_button),
			onClick = submit,
			enabled = !state.busy && state.waitSeconds == 0 && secret.isNotEmpty(),
			modifier = Modifier.fillMaxWidth(),
		)
		if (state.biometric && biometricsOnDevice) {
			SecondaryButton(
				stringResource(R.string.unlock_biometric),
				onClick = onBiometric,
				enabled = !state.busy,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Composable
internal fun kindLabel(kind: SecretKind) =
	stringResource(if (kind == SecretKind.PIN) R.string.lock_field_pin else R.string.lock_field_passphrase)

internal fun keyboardFor(kind: SecretKind) =
	if (kind == SecretKind.PIN) KeyboardType.NumberPassword else KeyboardType.Password
