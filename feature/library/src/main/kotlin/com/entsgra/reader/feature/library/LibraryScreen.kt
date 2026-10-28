package com.entsgra.reader.feature.library

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
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.component.WindowPlate
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen() {
	composable<LibraryRoute> { LibraryScreen() }
}

@Composable
internal fun LibraryScreen() {
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4),
	) {
		ScreenTitle(stringResource(R.string.library_title))
		SweepKeyCard {
			WindowPlate(
				label = stringResource(R.string.library_books),
				figure = "0",
				plateLabel = stringResource(R.string.library_folders),
				plateValue = "0",
			)
			BodyText(stringResource(R.string.library_empty))
		}
		PrimaryButton(
			text = stringResource(R.string.library_import),
			onClick = {},
			enabled = false,
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = ReaderSpacing.space6),
		)
	}
}
