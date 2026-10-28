package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/** Bottom bar: `bar-height` plus the gesture inset, `fog-raised`. 3 to 5 destinations. */
@Composable
fun ReaderBottomBar(
	modifier: Modifier = Modifier,
	content: @Composable RowScope.() -> Unit,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(ReaderTheme.colors.fogRaised)
			.navigationBarsPadding()
			.height(ReaderSize.barHeight)
			.selectableGroup(),
		content = content,
	)
}

/** Icon and one `label-small` line. The active tab gets the `stroke-trim` `action` edge on top. */
@Composable
fun RowScope.ReaderBottomBarItem(
	selected: Boolean,
	onClick: () -> Unit,
	icon: ImageVector,
	label: String,
) {
	val colors = ReaderTheme.colors
	val action = colors.action
	Column(
		modifier = Modifier
			.weight(1f)
			.fillMaxHeight()
			.selectable(selected = selected, role = Role.Tab, onClick = onClick)
			.drawBehind {
				if (selected) drawRect(action, size = Size(size.width, ReaderSize.strokeTrim.toPx()))
			},
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space1, Alignment.CenterVertically),
	) {
		CompositionLocalProvider(LocalContentColor provides if (selected) colors.ink else colors.inkMuted) {
			ReaderIcon(icon)
			Text(text = label.uppercase(), style = ReaderTheme.type.labelSmall, maxLines = 1)
		}
	}
}
