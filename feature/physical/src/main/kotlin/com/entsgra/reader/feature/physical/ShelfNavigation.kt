package com.entsgra.reader.feature.physical

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import kotlinx.serialization.Serializable

/** The Shelf tab: the shelf and the screen of each physical book. */
@Serializable
data object ShelfGraph

@Serializable
data object ShelfRoute

@Serializable
data class PhysicalBookRoute(val bookId: Long)

fun NavController.navigateToPhysicalBook(bookId: Long) = navigate(PhysicalBookRoute(bookId))

fun NavGraphBuilder.shelfGraph(navController: NavController) {
	navigation<ShelfGraph>(startDestination = ShelfRoute) {
		composable<ShelfRoute> {
			ShelfScreen(onOpenBook = navController::navigateToPhysicalBook)
		}
		composable<PhysicalBookRoute> {
			PhysicalBookScreen(onBack = { navController.popBackStack() })
		}
	}
}
