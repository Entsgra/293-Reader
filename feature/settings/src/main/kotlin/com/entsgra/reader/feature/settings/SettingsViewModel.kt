package com.entsgra.reader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.data.lock.AppLockState
import com.entsgra.reader.core.data.lock.AppLocks
import com.entsgra.reader.core.data.lock.AutoLock
import com.entsgra.reader.core.data.lock.UnlockResult
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.LockTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val settings: SettingsRepository,
	private val appLocks: AppLocks,
	private val autoLock: AutoLock,
) : ViewModel() {
	val theme: StateFlow<AppTheme?> = settings.theme
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	val lockTimeout: StateFlow<LockTimeout?> = settings.lockTimeout
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	val blockScreenshots: StateFlow<Boolean> = settings.blockScreenshots
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

	/** Null until read. */
	val appLock: StateFlow<AppLockState?> = appLocks.state

	fun setTheme(theme: AppTheme) {
		viewModelScope.launch { settings.setTheme(theme) }
	}

	fun setLockTimeout(timeout: LockTimeout) {
		viewModelScope.launch { settings.setLockTimeout(timeout) }
	}

	fun setBlockScreenshots(block: Boolean) {
		viewModelScope.launch { settings.setBlockScreenshots(block) }
	}

	/** After coming back from the setup screen. */
	fun refresh() {
		viewModelScope.launch { appLocks.refresh() }
	}

	suspend fun disableAppLock(secret: CharArray): UnlockResult = appLocks.disable(secret)

	suspend fun enrollCipher(): Cipher = appLocks.biometricEnrollCipher()

	fun enrolled(cipher: Cipher) {
		viewModelScope.launch { appLocks.biometricEnroll(cipher) }
	}

	fun stopBiometric() {
		viewModelScope.launch { appLocks.removeBiometric() }
	}

	/** Locks folders and, with the app lock on, ends the app until it is unlocked again. */
	fun lockNow() = autoLock.lockNow()
}
