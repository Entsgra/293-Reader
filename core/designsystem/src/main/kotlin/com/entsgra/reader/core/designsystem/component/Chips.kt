package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * Chips for 2 to 4 short options. Five or more options belong in a list.
 * The selected chip fills `action`; the rest are `fog-raised` with a `rib` outline.
 */
@Composable
fun <T> ReaderChips(
	options: List<T>,
	selected: T,
	onSelect: (T) -> Unit,
	label: (T) -> String,
	modifier: Modifier = Modifier,
) {
	require(options.size in 2..4) { "Chips take 2 to 4 options; use a list for ${options.size}" }
	Row(
		modifier = modifier
			.fillMaxWidth()
			.selectableGroup(),
		horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space2),
	) {
		options.forEach { option ->
			ReaderChip(
				text = label(option),
				selected = option == selected,
				onClick = { onSelect(option) },
				modifier = Modifier.weight(1f),
			)
		}
	}
}

@Composable
fun ReaderChip(
	text: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val colors = ReaderTheme.colors
	Box(
		modifier = modifier
			.heightIn(min = ReaderSize.targetMin)
			.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
			.then(
				if (selected) {
					Modifier.background(colors.action)
				} else {
					Modifier
						.background(colors.fogRaised)
						.border(ReaderSize.outline, colors.rib)
				},
			)
			.padding(horizontal = ReaderSpacing.space2),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text.uppercase(),
			style = ReaderTheme.type.label,
			color = if (selected) colors.onAction else colors.ink,
			textAlign = TextAlign.Center,
		)
	}
}
