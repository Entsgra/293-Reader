package com.entsgra.reader.feature.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import kotlinx.serialization.Serializable

/** The Library tab: the folder screens and the book screen under one graph. */
@Serializable
data object LibraryGraph

/** A folder's contents; null is the library root. */
@Serializable
data class LibraryRoute(val folderId: Long? = null)

@Serializable
data class BookRoute(val bookId: Long)

fun NavGraphBuilder.libraryGraph(navController: NavController) {
	navigation<LibraryGraph>(startDestination = LibraryRoute()) {
		composable<LibraryRoute> {
			LibraryScreen(
				onOpenFolder = { navController.navigate(LibraryRoute(it)) },
				onOpenBook = { navController.navigate(BookRoute(it)) },
				onBack = { navController.popBackStack() },
			)
		}
		composable<BookRoute> {
			BookScreen(onBack = { navController.popBackStack() })
		}
	}
}
