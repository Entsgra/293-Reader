package com.entsgra.reader.feature.lock

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import javax.crypto.Cipher

/** True when the phone has a strong biometric enrolled and working. */
fun biometricsAvailable(context: Context): Boolean =
	context.getSystemService(BiometricManager::class.java)?.canAuthenticate(BIOMETRIC_STRONG) ==
		BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows the system biometric prompt (BIOMETRIC_STRONG only) for a Keystore cipher, and hands
 * the unlocked cipher back on a match. Cancelling or failing leaves things as they were.
 */
class BiometricLauncher internal constructor(
	private val context: Context,
	private val cancelLabel: String,
) {
	fun authenticate(cipher: Cipher, title: String, onMatch: (Cipher) -> Unit) {
		val executor = context.mainExecutor
		val prompt = BiometricPrompt.Builder(context)
			.setTitle(title)
			.setAllowedAuthenticators(BIOMETRIC_STRONG)
			.setNegativeButton(cancelLabel, executor) { _, _ -> }
			.build()
		prompt.authenticate(
			BiometricPrompt.CryptoObject(cipher),
			CancellationSignal(),
			executor,
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					result.cryptoObject?.cipher?.let(onMatch)
				}
			},
		)
	}
}

@Composable
fun rememberBiometricLauncher(): BiometricLauncher {
	val context = LocalContext.current
	val cancel = stringResource(R.string.biometric_prompt_cancel)
	return remember(context, cancel) { BiometricLauncher(context, cancel) }
}
