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
import com.entsgra.reader.feature.library.LibraryRoute
import com.entsgra.reader.feature.library.libraryScreen
import com.entsgra.reader.feature.log.logScreen
import com.entsgra.reader.feature.physical.shelfScreen
import com.entsgra.reader.feature.settings.settingsScreen

/** Navigation shell: four top-level destinations in a bottom bar. Fog edge to edge. */
@Composable
fun ReaderApp() {
	val navController = rememberNavController()
	val backStack by navController.currentBackStackEntryAsState()
	val destination = backStack?.destination

	Column(
		Modifier
			.fillMaxSize()
			.background(ReaderTheme.colors.fog),
	) {
		NavHost(
			navController = navController,
			startDestination = LibraryRoute,
			modifier = Modifier
				.weight(1f)
				.windowInsetsPadding(
					WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
				),
			// Nothing slides sideways: destinations swap in place.
			enterTransition = { EnterTransition.None },
			exitTransition = { ExitTransition.None },
			popEnterTransition = { EnterTransition.None },
			popExitTransition = { ExitTransition.None },
		) {
			libraryScreen()
			shelfScreen()
			logScreen()
			settingsScreen()
		}
		ReaderBottomBar {
			TopLevelDestination.entries.forEach { item ->
				val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
				ReaderBottomBarItem(
					selected = selected,
					onClick = {
						navController.navigate(item.route) {
							popUpTo(navController.graph.findStartDestination().id) { saveState = true }
							launchSingleTop = true
							restoreState = true
						}
					},
					icon = item.icon,
					label = stringResource(item.label),
				)
			}
		}
	}
}
