package com.entsgra.reader.feature.reader

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** A book open for reading. Full screen: no bottom bar, content under the system bars. */
@Serializable
data class ReaderRoute(val bookId: Long)

fun NavController.navigateToReader(bookId: Long) = navigate(ReaderRoute(bookId))

fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
	composable<ReaderRoute> {
		ReaderScreen(onBack = onBack)
	}
}
