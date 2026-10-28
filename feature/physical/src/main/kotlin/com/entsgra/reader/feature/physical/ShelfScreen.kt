package com.entsgra.reader.feature.physical

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import kotlinx.serialization.Serializable

@Serializable
data object ShelfRoute

fun NavGraphBuilder.shelfScreen() {
	composable<ShelfRoute> { ShelfScreen() }
}

@Composable
internal fun ShelfScreen() {
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4),
	) {
		ScreenTitle(stringResource(R.string.physical_title))
		BodyText(stringResource(R.string.physical_empty))
		PrimaryButton(
			text = stringResource(R.string.physical_add),
			onClick = {},
			enabled = false,
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = ReaderSpacing.space6),
		)
	}
}
