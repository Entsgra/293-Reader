package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.core.designsystem.theme.ReaderShapes
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * Screen title in `display`, uppercase, with `space-12` of air above. Stands alone. With
 * [onBack], a back button sits in that air, its icon lined up with the title.
 */
@Composable
fun ScreenTitle(
	text: String,
	modifier: Modifier = Modifier,
	onBack: (() -> Unit)? = null,
	backLabel: String? = null,
) {
	Column(modifier) {
		if (onBack != null) {
			ReaderIconButton(
				icon = ReaderIcons.Back,
				label = backLabel.orEmpty(),
				onClick = onBack,
				modifier = Modifier.offset(x = -(ReaderSize.targetMin - ReaderSize.icon) / 2),
			)
		}
		Text(
			text = text.uppercase(),
			style = ReaderTheme.type.display,
			color = ReaderTheme.colors.ink,
			modifier = Modifier
				.padding(top = if (onBack == null) ReaderSpacing.space12 else 0.dp, bottom = ReaderSpacing.space6)
				.semantics { heading() },
		)
	}
}

/**
 * An icon-only button, `target-min` square, for universal actions only (back, close). [label]
 * is read by screen readers.
 */
@Composable
fun ReaderIconButton(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.size(ReaderSize.targetMin)
			.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
			.semantics { contentDescription = label },
		contentAlignment = Alignment.Center,
	) {
		CompositionLocalProvider(LocalContentColor provides ReaderTheme.colors.ink) {
			ReaderIcon(icon)
		}
	}
}

/** Section title in `headline`, uppercase. */
@Composable
fun SectionHeadline(text: String, modifier: Modifier = Modifier) {
	Text(
		text = text.uppercase(),
		style = ReaderTheme.type.headline,
		color = ReaderTheme.colors.ink,
		modifier = modifier.semantics { heading() },
	)
}

/** One sentence of explanation in `body`. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
	Text(text = text, style = ReaderTheme.type.body, color = ReaderTheme.colors.ink, modifier = modifier)
}

/** Uppercase `label` in `ink-muted`, for the label above a figure or field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
	Text(text = text.uppercase(), style = ReaderTheme.type.label, color = ReaderTheme.colors.inkMuted, modifier = modifier)
}

/** A `rib` rule. */
@Composable
fun Rule(modifier: Modifier = Modifier, thickness: Dp = ReaderSize.rule) {
	Box(
		modifier
			.fillMaxWidth()
			.height(thickness)
			.background(ReaderTheme.colors.rib),
	)
}

/**
 * A one-line row: a title and at most one trailing value (DONE, 47%, NOV 10).
 * `row-height` tall, ruled underneath. Wraps at the largest text sizes.
 */
@Composable
fun ReaderRow(
	title: String,
	modifier: Modifier = Modifier,
	trailing: String? = null,
	leadingIcon: ImageVector? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	onLongClickLabel: String? = null,
	color: Color = ReaderTheme.colors.ink,
) {
	val colors = ReaderTheme.colors
	Column(modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = ReaderSize.rowHeight)
				.then(
					if (onClick != null || onLongClick != null) {
						Modifier.combinedClickable(
							role = Role.Button,
							onLongClickLabel = onLongClickLabel,
							onLongClick = onLongClick,
							onClick = onClick ?: {},
						)
					} else {
						Modifier
					},
				)
				.padding(vertical = ReaderSpacing.space2),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
		) {
			if (leadingIcon != null) {
				CompositionLocalProvider(LocalContentColor provides color) { ReaderIcon(leadingIcon) }
			}
			Text(
				text = title,
				style = ReaderTheme.type.title,
				color = color,
				modifier = Modifier.weight(1f),
			)
			if (trailing != null) {
				Text(text = trailing.uppercase(), style = ReaderTheme.type.label, color = colors.inkMuted)
			}
		}
		Rule()
	}
}

/** A 24dp stroke icon tinted with the current content colour. Decorative unless described. */
@Composable
fun ReaderIcon(icon: ImageVector, modifier: Modifier = Modifier, contentDescription: String? = null) {
	Icon(
		imageVector = icon,
		contentDescription = contentDescription,
		tint = LocalContentColor.current,
		modifier = modifier.size(ReaderSize.icon),
	)
}

/** Live status: a 10dp `banner` dot followed by an uppercase label. The one red thing. */
@Composable
fun LiveStatus(text: String, modifier: Modifier = Modifier) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space2),
	) {
		StatusDot()
		Text(text = text.uppercase(), style = ReaderTheme.type.label, color = ReaderTheme.colors.ink)
	}
}

@Composable
fun StatusDot(modifier: Modifier = Modifier) {
	Box(
		modifier
			.size(ReaderSize.statusDot)
			.background(ReaderTheme.colors.banner, ReaderShapes.round),
	)
}

/** Error: `banner` outline, a red disc with `!`, and one sentence with the fix. */
@Composable
fun ErrorNotice(message: String, modifier: Modifier = Modifier) {
	val colors = ReaderTheme.colors
	Row(
		modifier = modifier
			.fillMaxWidth()
			.border(ReaderSize.heavyRule, colors.banner)
			.padding(ReaderSpacing.space4),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
	) {
		Box(
			modifier = Modifier
				.size(ReaderSize.errorDisc)
				.background(colors.banner, ReaderShapes.round),
			contentAlignment = Alignment.Center,
		) {
			Text(text = "!", style = ReaderTheme.type.label, color = colors.onBanner, textAlign = TextAlign.Center)
		}
		Text(text = message, style = ReaderTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
	}
}
