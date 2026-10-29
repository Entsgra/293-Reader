package com.entsgra.reader.feature.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.entsgra.reader.core.data.lock.UnlockResult
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.DestructiveButton
import com.entsgra.reader.core.designsystem.component.DialogButtons
import com.entsgra.reader.core.designsystem.component.LiveStatus
import com.entsgra.reader.core.designsystem.component.ReaderDialog
import com.entsgra.reader.core.designsystem.component.ReaderTextField
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.theme.SecureWindow
import com.entsgra.reader.core.model.SecretKind
import kotlinx.coroutines.launch

/**
 * Asks for the secret before something that weakens a lock: removing it, or turning the app
 * lock off. Closes itself once [onConfirm] accepts the secret.
 */
@Composable
fun ConfirmSecretDialog(
	title: String,
	body: String,
	kind: SecretKind,
	confirmLabel: String,
	onConfirm: suspend (CharArray) -> UnlockResult,
	onDismiss: () -> Unit,
) {
	SecureWindow()
	var secret by remember { mutableStateOf("") }
	var result by remember { mutableStateOf<UnlockResult?>(null) }
	var busy by remember { mutableStateOf(false) }
	val scope = rememberCoroutineScope()
	val submit: () -> Unit = {
		if (!busy && secret.isNotEmpty()) {
			busy = true
			scope.launch {
				val outcome = onConfirm(secret.toCharArray())
				busy = false
				secret = ""
				if (outcome == UnlockResult.Ok) onDismiss() else result = outcome
			}
		}
	}
	ReaderDialog(title = title, onDismiss = onDismiss) {
		BodyText(body)
		ReaderTextField(
			value = secret,
			onValueChange = {
				secret = it
				result = null
			},
			label = kindLabel(kind),
			error = when (val failed = result) {
				is UnlockResult.Wait -> stringResource(R.string.unlock_wait, formatWait(((failed.waitMs + 999) / 1000).toInt()))
				is UnlockResult.Wrong -> stringResource(
					if (kind == SecretKind.PIN) R.string.unlock_wrong_pin else R.string.unlock_wrong_passphrase,
				)
				else -> null
			},
			keyboardType = keyboardFor(kind),
			secret = true,
			onImeAction = submit,
		)
		if (busy) LiveStatus(stringResource(R.string.unlock_checking))
		DialogButtons {
			DestructiveButton(confirmLabel, onClick = submit, enabled = !busy && secret.isNotEmpty(), modifier = Modifier.fillMaxWidth())
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
}
