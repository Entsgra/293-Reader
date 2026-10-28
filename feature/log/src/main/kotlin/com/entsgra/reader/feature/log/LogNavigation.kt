package com.entsgra.reader.feature.log

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LogRoute

/** Rows open their book: [onOpenEbook] in the library, [onOpenPhysical] on the shelf. */
fun NavGraphBuilder.logScreen(onOpenEbook: (Long) -> Unit, onOpenPhysical: (Long) -> Unit) {
	composable<LogRoute> { LogScreen(onOpenEbook = onOpenEbook, onOpenPhysical = onOpenPhysical) }
}
