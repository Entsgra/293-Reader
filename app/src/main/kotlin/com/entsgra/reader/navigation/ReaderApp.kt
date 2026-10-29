package com.entsgra.reader.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.entsgra.reader.core.designsystem.component.ReaderBottomBar
import com.entsgra.reader.core.designsystem.component.ReaderBottomBarItem
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.feature.library.BookRoute
import com.entsgra.reader.feature.library.LibraryGraph
import com.entsgra.reader.feature.library.libraryGraph
import com.entsgra.reader.feature.lock.lockSetupScreen
import com.entsgra.reader.feature.lock.navigateToLockSetup
import com.entsgra.reader.feature.log.logScreen
import com.entsgra.reader.feature.reader.ReaderRoute
import com.entsgra.reader.feature.reader.navigateToReader
import com.entsgra.reader.feature.reader.readerScreen
import com.entsgra.reader.feature.physical.navigateToPhysicalBook
import com.entsgra.reader.feature.physical.shelfGraph
import com.entsgra.reader.feature.settings.settingsScreen

/** Navigation shell: four top-level destinations in a bottom bar. Fog edge to edge. */
@Composable
fun ReaderApp() {
	val navController = rememberNavController()
	val backStack by navController.currentBackStackEntryAsState()
	val destination = backStack?.destination
	// The reader runs edge to edge under the system bars, with no bottom bar.
	val reading = destination?.hasRoute<ReaderRoute>() == true

	Column(
		Modifier
			.fillMaxSize()
			.background(ReaderTheme.colors.fog),
	) {
		NavHost(
			navController = navController,
			startDestination = LibraryGraph,
			modifier = Modifier
				.weight(1f)
				.then(
					if (reading) {
						Modifier
					} else {
						Modifier.windowInsetsPadding(
							WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
						)
					},
				),
			// Nothing slides sideways: destinations swap in place.
			enterTransition = { EnterTransition.None },
			exitTransition = { ExitTransition.None },
			popEnterTransition = { EnterTransition.None },
			popExitTransition = { ExitTransition.None },
		) {
			libraryGraph(
				navController,
				onRead = navController::navigateToReader,
				onLockSetup = { folderId, change -> navController.navigateToLockSetup(folderId, change) },
			)
			lockSetupScreen(onDone = { navController.popBackStack() })
			readerScreen(onBack = { navController.popBackStack() })
			shelfGraph(navController)
			logScreen(
				onOpenEbook = { navController.navigate(BookRoute(it)) },
				onOpenPhysical = navController::navigateToPhysicalBook,
			)
			settingsScreen(onAppLockSetup = { change -> navController.navigateToLockSetup(null, change) })
		}
		if (!reading) ReaderBottomBar {
			TopLevelDestination.entries.forEach { item ->
				val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
				ReaderBottomBarItem(
					selected = selected,
					onClick = {
						if (selected) {
							// Tapping the open tab again returns to its first screen.
							navController.navigate(item.route) {
								popUpTo(item.route) { inclusive = true }
							}
						} else {
							navController.navigate(item.route) {
								popUpTo(navController.graph.findStartDestination().id) { saveState = true }
								launchSingleTop = true
								restoreState = true
							}
						}
					},
					icon = item.icon,
					label = stringResource(item.label),
				)
			}
		}
	}
}
