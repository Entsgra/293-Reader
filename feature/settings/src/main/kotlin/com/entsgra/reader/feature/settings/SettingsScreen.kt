package com.entsgra.reader.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.entsgra.reader.core.designsystem.component.ReaderChips
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SectionHeadline
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.AppTheme
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen() {
	composable<SettingsRoute> {
		val viewModel: SettingsViewModel = hiltViewModel()
		val theme by viewModel.theme.collectAsStateWithLifecycle()
		SettingsScreen(theme = theme, onThemeChange = viewModel::setTheme)
	}
}

@Composable
internal fun SettingsScreen(
	theme: AppTheme?,
	onThemeChange: (AppTheme) -> Unit,
) {
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4),
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
	}
}
