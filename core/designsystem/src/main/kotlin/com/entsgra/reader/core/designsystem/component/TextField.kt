package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * A one-line input: an uppercase label above a `fog-raised` face with a `rib` outline. Focus
 * draws the standard ring. An error turns the outline `banner` and puts one sentence below.
 */
@Composable
fun ReaderTextField(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	placeholder: String? = null,
	leadingIcon: ImageVector? = null,
	trailing: (@Composable () -> Unit)? = null,
	error: String? = null,
	imeAction: ImeAction = ImeAction.Done,
	capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
	keyboardType: KeyboardType = KeyboardType.Text,
	onImeAction: () -> Unit = {},
) {
	val colors = ReaderTheme.colors
	val interaction = remember { MutableInteractionSource() }
	Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space2)) {
		FieldLabel(label)
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			singleLine = true,
			textStyle = ReaderTheme.type.body.copy(color = colors.ink),
			cursorBrush = SolidColor(colors.action),
			interactionSource = interaction,
			keyboardOptions = KeyboardOptions(
				capitalization = capitalization,
				keyboardType = keyboardType,
				imeAction = imeAction,
			),
			keyboardActions = KeyboardActions(onAny = { onImeAction() }),
			modifier = Modifier
				.fillMaxWidth()
				.semantics { if (error != null) error(error) },
			decorationBox = { inner ->
				Row(
					modifier = Modifier
						.indication(interaction, LocalIndication.current)
						.background(colors.fogRaised)
						.border(
							if (error != null) ReaderSize.heavyRule else ReaderSize.outline,
							if (error != null) colors.banner else colors.rib,
						)
						.heightIn(min = ReaderSize.targetMin)
						.padding(start = ReaderSpacing.space3),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space2),
				) {
					if (leadingIcon != null) {
						CompositionLocalProvider(LocalContentColor provides colors.inkMuted) {
							ReaderIcon(leadingIcon)
						}
					}
					Box(
						Modifier
							.weight(1f)
							.padding(end = ReaderSpacing.space3, top = ReaderSpacing.space2, bottom = ReaderSpacing.space2),
					) {
						if (value.isEmpty() && placeholder != null) {
							Text(placeholder, style = ReaderTheme.type.body, color = colors.inkMuted, maxLines = 1)
						}
						inner()
					}
					trailing?.invoke()
				}
			},
		)
		if (error != null) {
			Text(error, style = ReaderTheme.type.body, color = colors.banner)
		}
	}
}
