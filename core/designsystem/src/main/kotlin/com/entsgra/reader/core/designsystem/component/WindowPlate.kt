package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * Window plate: the [figure] on the left under [label]; a `plate-width` plate on the right,
 * behind a 2px `rib` divider, holding [plateValue] under [plateLabel].
 * In the reader: PAGE 47 | OF 312.
 */
@Composable
fun WindowPlate(
	label: String,
	figure: String,
	plateLabel: String,
	plateValue: String,
	modifier: Modifier = Modifier,
) {
	val colors = ReaderTheme.colors
	val type = ReaderTheme.type
	Row(
		modifier = modifier
			.height(IntrinsicSize.Min)
			.clearAndSetSemantics { contentDescription = "$label $figure, $plateLabel $plateValue" },
	) {
		Column(Modifier.weight(1f)) {
			Text(label.uppercase(), style = type.label, color = colors.inkMuted)
			Text(figure, style = type.figure, color = colors.ink)
		}
		Box(
			Modifier
				.width(ReaderSize.heavyRule)
				.fillMaxHeight()
				.background(colors.rib),
		)
		Column(
			Modifier
				.width(ReaderSize.plateWidth - ReaderSize.heavyRule)
				.padding(start = ReaderSpacing.space3),
		) {
			Text(plateLabel.uppercase(), style = type.label, color = colors.inkMuted)
			Text(plateValue, style = type.time, color = colors.ink)
		}
	}
}
