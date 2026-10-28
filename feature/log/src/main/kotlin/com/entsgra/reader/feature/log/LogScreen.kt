package com.entsgra.reader.feature.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import kotlinx.serialization.Serializable

@Serializable
data object LogRoute

fun NavGraphBuilder.logScreen() {
	composable<LogRoute> { LogScreen() }
}

@Composable
internal fun LogScreen() {
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4),
	) {
		ScreenTitle(stringResource(R.string.log_title))
		BodyText(stringResource(R.string.log_empty))
	}
}
