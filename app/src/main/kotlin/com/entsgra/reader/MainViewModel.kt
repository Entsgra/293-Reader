package com.entsgra.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
	/** Null until the stored theme is read, so the first frame never flashes the wrong theme. */
	val theme: StateFlow<AppTheme?> = settings.theme
		.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
