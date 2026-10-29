package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.model.SecretKind
import com.entsgra.reader.core.security.AppLock
import com.entsgra.reader.core.security.BiometricInvalidatedException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The app lock as settings show it. */
data class AppLockState(val enabled: Boolean, val kind: SecretKind?, val biometric: Boolean)

/**
 * The app-wide lock: the database passphrase and the app's file keyset sealed under a secret.
 * Locking it again ends the process (see the app module), so once open it stays open.
 */
@Singleton
class AppLocks @Inject constructor(private val appLock: AppLock) {
	/** True when there is no app lock, or it was opened in this process. */
	val isOpen: StateFlow<Boolean> = appLock.isOpen

	val isEnabled: Boolean get() = appLock.isEnabled

	private val _state = MutableStateFlow<AppLockState?>(null)

	/** Null until first read. */
	val state: StateFlow<AppLockState?> = _state.asStateFlow()

	suspend fun refresh(): AppLockState = withContext(Dispatchers.IO) {
		val pin = appLock.usesPin()
		AppLockState(
			enabled = pin != null,
			kind = pin?.let { if (it) SecretKind.PIN else SecretKind.PASSPHRASE },
			biometric = pin != null && appLock.hasBiometric(),
		).also { _state.value = it }
	}

	/** Zeroes [secret]. */
	suspend fun enable(kind: SecretKind, secret: CharArray) {
		withContext(Dispatchers.Default) { appLock.enable(kind == SecretKind.PIN, secret) }
		refresh()
	}

	suspend fun unlock(secret: CharArray): UnlockResult =
		withContext(Dispatchers.Default) { appLock.unlock(secret) }.asUnlockResult()

	suspend fun waitMs(): Long = withContext(Dispatchers.IO) { appLock.waitMs() }

	suspend fun biometricStart(): BiometricStart = withContext(Dispatchers.IO) {
		if (!appLock.hasBiometric()) return@withContext BiometricStart.None
		try {
			BiometricStart.Ready(appLock.biometricUnlockCipher())
		} catch (_: BiometricInvalidatedException) {
			appLock.removeBiometric()
			BiometricStart.Invalidated
		}
	}.also { refresh() }

	suspend fun biometricUnlock(cipher: Cipher) = withContext(Dispatchers.IO) { appLock.biometricUnlock(cipher) }

	suspend fun biometricEnrollCipher(): Cipher = withContext(Dispatchers.IO) { appLock.biometricEnrollCipher() }

	suspend fun biometricEnroll(cipher: Cipher) {
		withContext(Dispatchers.IO) { appLock.biometricEnroll(cipher) }
		refresh()
	}

	suspend fun removeBiometric() {
		withContext(Dispatchers.IO) { appLock.removeBiometric() }
		refresh()
	}

	/** Zeroes both secrets. */
	suspend fun changeSecret(current: CharArray, kind: SecretKind, new: CharArray): UnlockResult {
		val result = withContext(Dispatchers.Default) { appLock.changeSecret(current, kind == SecretKind.PIN, new) }
		refresh()
		return result.asUnlockResult()
	}

	/** Zeroes [secret]. */
	suspend fun disable(secret: CharArray): UnlockResult {
		val result = withContext(Dispatchers.Default) { appLock.disable(secret) }
		refresh()
		return result.asUnlockResult()
	}

	internal val opened: Boolean get() = appLock.isEnabled && appLock.isOpen.value
}
