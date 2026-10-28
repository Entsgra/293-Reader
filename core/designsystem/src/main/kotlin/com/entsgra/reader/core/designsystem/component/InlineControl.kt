package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.entsgra.reader.core.designsystem.theme.ReaderShapes
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * A switch that affects one block, placed inside that block above a 1px `rib` rule.
 * Only app-wide settings go on the settings screen.
 */
@Composable
fun InlineControl(
	label: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val colors = ReaderTheme.colors
	Column(modifier.fillMaxWidth()) {
		Rule()
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = ReaderSize.rowHeight)
				.toggleable(
					value = checked,
					enabled = enabled,
					role = Role.Switch,
					onValueChange = onCheckedChange,
				),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = label.uppercase(),
				style = ReaderTheme.type.label,
				color = if (enabled) colors.ink else colors.inkMuted,
				modifier = Modifier.weight(1f),
			)
			Spacer(Modifier.width(ReaderSpacing.space3))
			ReaderSwitch(checked = checked, enabled = enabled)
		}
	}
}

/** Visual-only switch: square track, round knob. Toggle semantics live on the parent row. */
@Composable
fun ReaderSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
	val colors = ReaderTheme.colors
	val travel = ReaderSize.switchTrackWidth - ReaderSize.switchKnob - ReaderSpacing.space1 * 2
	// No slide: the design forbids sideways motion, so the knob moves in one step.
	val knobOffset = if (checked) travel else 0.dp
	val track = when {
		!enabled -> colors.ledge
		checked -> colors.action
		else -> colors.fogRaised
	}
	val knob = when {
		!enabled -> colors.inkMuted
		checked -> colors.onAction
		else -> colors.rib
	}
	Box(
		modifier = modifier
			.size(ReaderSize.switchTrackWidth, ReaderSize.switchTrackHeight)
			.background(track)
			.then(
				if (checked || !enabled) Modifier else Modifier.border(ReaderSize.outline, colors.rib),
			),
		contentAlignment = Alignment.CenterStart,
	) {
		Box(
			Modifier
				.offset(x = ReaderSpacing.space1 + knobOffset)
				.size(ReaderSize.switchKnob)
				.background(knob, ReaderShapes.round),
		)
	}
}
