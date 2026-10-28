package com.entsgra.reader.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.entsgra.reader.core.designsystem.theme.ReaderMotion
import com.entsgra.reader.core.designsystem.theme.ReaderShapes
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * A terrace: a face sitting on a `ledge` that steps out [ReaderSize.ledgeStep] below it.
 * Pressing drops the face [ReaderSize.pressDrop] onto the ledge. Disabled: `ledge` fill,
 * `ink-muted` content, no ledge. The ledge space is always reserved so layout never jumps.
 *
 * The press offset is read in the draw phase only; pressing never recomposes or relayouts.
 */
@Composable
fun Terrace(
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)? = null,
	enabled: Boolean = true,
	face: Color = ReaderTheme.colors.fogRaised,
	contentColor: Color = ReaderTheme.colors.ink,
	shape: Shape = ReaderShapes.none,
	role: Role = Role.Button,
	onClickLabel: String? = null,
	onLongClick: (() -> Unit)? = null,
	onLongClickLabel: String? = null,
	content: @Composable BoxScope.() -> Unit,
) {
	val colors = ReaderTheme.colors
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val drop = animateDpAsState(
		targetValue = if (pressed && enabled && onClick != null) ReaderSize.pressDrop else 0.dp,
		animationSpec = tween(ReaderMotion.DURATION_MS, easing = ReaderMotion.easing),
		label = "terrace-press",
	)
	val ledgeColor = colors.ledge
	val faceColor = if (enabled) face else colors.ledge
	val resolvedContent = if (enabled) contentColor else colors.inkMuted

	Box(
		modifier = modifier
			.then(
				if (enabled) {
					Modifier.drawWithCache {
						val step = ReaderSize.ledgeStep.toPx()
						val outline = shape.createOutline(
							Size(size.width, size.height - step),
							layoutDirection,
							this,
						)
						onDrawBehind { translate(top = step) { drawOutline(outline, ledgeColor) } }
					}
				} else {
					Modifier
				},
			)
			.padding(bottom = ReaderSize.ledgeStep),
		propagateMinConstraints = true,
	) {
		Box(
			modifier = Modifier
				.graphicsLayer { translationY = drop.value.toPx() }
				.then(
					if (onClick != null) {
						Modifier.combinedClickable(
							interactionSource = interaction,
							indication = LocalIndication.current,
							enabled = enabled,
							onClickLabel = onClickLabel,
							role = role,
							onLongClickLabel = onLongClickLabel,
							onLongClick = onLongClick,
							onClick = onClick,
						)
					} else {
						Modifier
					},
				)
				// Clip after clickable so the focus ring can draw outside the face.
				.clip(shape)
				.background(faceColor),
			propagateMinConstraints = true,
		) {
			CompositionLocalProvider(LocalContentColor provides resolvedContent) {
				content()
			}
		}
	}
}
