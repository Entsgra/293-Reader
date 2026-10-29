package com.entsgra.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.data.library.BookImporter
import com.entsgra.reader.core.data.library.ImportState
import com.entsgra.reader.core.data.lock.AppLockState
import com.entsgra.reader.core.data.lock.AppLocks
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.feature.lock.UnlockController
import com.entsgra.reader.feature.lock.UnlockOps
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
	settings: SettingsRepository,
	private val appLocks: AppLocks,
	importer: BookImporter,
	guard: AppLockGuard,
) : ViewModel() {
	/** Null until the stored theme is read, so the first frame never flashes the wrong theme. */
	val theme: StateFlow<AppTheme?> = settings.theme
		.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	val blockScreenshots: StateFlow<Boolean> = settings.blockScreenshots
		.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/** False while the app lock is closed: show its unlock screen and nothing else. */
	val appOpen: StateFlow<Boolean> = appLocks.isOpen

	/** Null until read; tells the unlock screen which keyboard to show. */
	val appLock: StateFlow<AppLockState?> = appLocks.state

	/** The app lock fell due while a book was being imported; it locks once that book is in. */
	val lockingAfterImport: StateFlow<Boolean> = combine(guard.ending, importer.state) { ending, import ->
		ending && import is ImportState.Running
	}.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	val unlock = UnlockController(
		viewModelScope,
		object : UnlockOps {
			override suspend fun unlock(secret: CharArray) = appLocks.unlock(secret)
			override suspend fun waitMs() = appLocks.waitMs()
			override suspend fun hasBiometric() = appLocks.refresh().biometric
			override suspend fun biometricStart() = appLocks.biometricStart()
			override suspend fun biometricUnlock(cipher: Cipher) = appLocks.biometricUnlock(cipher)
			override suspend fun biometricEnrollCipher() = appLocks.biometricEnrollCipher()
			override suspend fun biometricEnroll(cipher: Cipher) = appLocks.biometricEnroll(cipher)
		},
	)

	init {
		viewModelScope.launch { appLocks.refresh() }
	}
}
