package com.entsgra.reader.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.entsgra.reader.R
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.feature.library.LibraryGraph
import com.entsgra.reader.feature.log.LogRoute
import com.entsgra.reader.feature.physical.ShelfGraph
import com.entsgra.reader.feature.settings.SettingsRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
	val route: Any,
	val routeClass: KClass<*>,
	val icon: ImageVector,
	@StringRes val label: Int,
) {
	LIBRARY(LibraryGraph, LibraryGraph::class, ReaderIcons.Library, R.string.nav_library),
	SHELF(ShelfGraph, ShelfGraph::class, ReaderIcons.Shelf, R.string.nav_shelf),
	LOG(LogRoute, LogRoute::class, ReaderIcons.Log, R.string.nav_log),
	SETTINGS(SettingsRoute, SettingsRoute::class, ReaderIcons.Settings, R.string.nav_settings),
}
