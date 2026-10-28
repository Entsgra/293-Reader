package com.entsgra.reader.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Stroke icons: 24 grid, 2 stroke, square caps, no fill. The stroke colour is a placeholder;
 * icons are tinted with the current content colour where drawn.
 */
object ReaderIcons {
	val Library by lazy { stroke("Library", "M4 4h4v16H4z M10 4h4v16h-4z M16 5l3.5-1 3 15-3.5 1z") }
	val Shelf by lazy { stroke("Shelf", "M2 5h8l2 2 2-2h8v14h-8l-2 2-2-2H2z M12 7v14") }
	val Log by lazy { stroke("Log", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 3") }
	val Settings by lazy { stroke("Settings", "M4 6h16 M4 12h16 M4 18h16 M8 4v4 M16 10v4 M10 16v4") }
	val Add by lazy { stroke("Add", "M12 5v14 M5 12h14") }
	val Search by lazy { stroke("Search", "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M15.5 15.5L20 20") }
	val Folder by lazy { stroke("Folder", "M3 5h7l2 2h9v12H3z") }
	val Lock by lazy { stroke("Lock", "M5 11h14v10H5z M8 11V7a4 4 0 0 1 8 0v4") }
	val Back by lazy { stroke("Back", "M20 12H5 M11 6l-6 6 6 6") }
	val Bookmark by lazy { stroke("Bookmark", "M6 3h12v18l-6-5-6 5z") }
	val Check by lazy { stroke("Check", "M5 12l5 5 9-10") }
	val Close by lazy { stroke("Close", "M6 6l12 12 M18 6L6 18") }
	val Sort by lazy { stroke("Sort", "M4 6h16 M4 12h10 M4 18h4") }
}

private fun stroke(name: String, pathData: String): ImageVector =
	ImageVector.Builder(
		name = name,
		defaultWidth = 24.dp,
		defaultHeight = 24.dp,
		viewportWidth = 24f,
		viewportHeight = 24f,
	).addPath(
		pathData = addPathNodes(pathData),
		fill = null,
		stroke = SolidColor(Color.Black),
		strokeLineWidth = 2f,
		strokeLineCap = StrokeCap.Square,
		strokeLineJoin = StrokeJoin.Miter,
	).build()
