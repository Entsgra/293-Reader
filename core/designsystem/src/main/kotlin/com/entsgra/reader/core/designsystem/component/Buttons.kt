package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/** The one primary action per screen: an `action` terrace. */
@Composable
fun PrimaryButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val colors = ReaderTheme.colors
	Terrace(
		modifier = modifier,
		onClick = onClick,
		enabled = enabled,
		face = colors.action,
		contentColor = colors.onAction,
	) {
		ButtonLabel(text)
	}
}

/** Flat `fog-raised` button with a `rib` outline. Pressed: `ledge` fill. */
@Composable
fun SecondaryButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val colors = ReaderTheme.colors
	FlatButton(
		text = text,
		onClick = onClick,
		modifier = modifier,
		enabled = enabled,
		fill = colors.fogRaised,
		pressedFill = colors.ledge,
		outline = colors.rib,
		outlineWidth = ReaderSize.outline,
		textColor = colors.ink,
	)
}

/** Destructive actions never take the primary style: `banner` text and a 2px `banner` outline. */
@Composable
fun DestructiveButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val colors = ReaderTheme.colors
	FlatButton(
		text = text,
		onClick = onClick,
		modifier = modifier,
		enabled = enabled,
		fill = colors.fog,
		pressedFill = colors.ledge,
		outline = colors.banner,
		outlineWidth = ReaderSize.heavyRule,
		textColor = colors.banner,
	)
}

@Composable
private fun FlatButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier,
	enabled: Boolean,
	fill: Color,
	pressedFill: Color,
	outline: Color,
	outlineWidth: Dp,
	textColor: Color,
) {
	val colors = ReaderTheme.colors
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	Box(
		modifier = modifier
			.clickable(
				interactionSource = interaction,
				indication = LocalIndication.current,
				enabled = enabled,
				role = Role.Button,
				onClick = onClick,
			)
			.background(
				when {
					!enabled -> colors.ledge
					pressed -> pressedFill
					else -> fill
				},
			)
			.then(if (enabled) Modifier.border(outlineWidth, outline) else Modifier),
		propagateMinConstraints = true,
	) {
		CompositionLocalProvider(LocalContentColor provides if (enabled) textColor else colors.inkMuted) {
			ButtonLabel(text)
		}
	}
}

@Composable
private fun ButtonLabel(text: String) {
	Box(
		modifier = Modifier
			.heightIn(min = ReaderSize.targetMin)
			.padding(horizontal = ReaderSpacing.space5),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text.uppercase(),
			style = ReaderTheme.type.label,
			textAlign = TextAlign.Center,
		)
	}
}
