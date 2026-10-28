package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import com.entsgra.reader.core.designsystem.theme.ReaderShapes
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * The key card: the one block per screen with the sweep (bottom-right) and a `stroke-trim`
 * `action` edge along its top. A terrace, so it sits on a ledge.
 */
@Composable
fun SweepKeyCard(
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val action = ReaderTheme.colors.action
	Terrace(
		modifier = modifier.fillMaxWidth(),
		onClick = onClick,
		shape = ReaderShapes.sweep,
	) {
		Column(
			modifier = Modifier
				.drawBehind {
					drawRect(action, size = Size(size.width, ReaderSize.strokeTrim.toPx()))
				}
				.padding(
					start = ReaderSpacing.space4,
					end = ReaderSpacing.space4,
					top = ReaderSpacing.space4 + ReaderSize.strokeTrim,
					// Room for the sweep so content clears the curve.
					bottom = ReaderSpacing.space8,
				),
			verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
			content = content,
		)
	}
}
