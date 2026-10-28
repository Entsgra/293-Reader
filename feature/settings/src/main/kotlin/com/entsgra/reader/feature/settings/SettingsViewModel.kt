package com.entsgra.reader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val settings: SettingsRepository,
) : ViewModel() {
	val theme: StateFlow<AppTheme?> = settings.theme
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	fun setTheme(theme: AppTheme) {
		viewModelScope.launch { settings.setTheme(theme) }
	}
}
