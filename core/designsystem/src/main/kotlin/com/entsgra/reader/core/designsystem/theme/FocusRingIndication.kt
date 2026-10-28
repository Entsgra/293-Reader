package com.entsgra.reader.core.designsystem.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/** Focus: 3px solid `action`, offset 2px. No ripple, no press overlay. */
internal class FocusRingIndication(private val color: Color) : IndicationNodeFactory {
	override fun create(interactionSource: InteractionSource): DelegatableNode =
		FocusRingNode(interactionSource, color)

	override fun equals(other: Any?): Boolean = other is FocusRingIndication && other.color == color

	override fun hashCode(): Int = color.hashCode()
}

private class FocusRingNode(
	private val interactionSource: InteractionSource,
	private val color: Color,
) : Modifier.Node(), DrawModifierNode {
	private var focused = false

	override fun onAttach() {
		coroutineScope.launch {
			var count = 0
			interactionSource.interactions.collect { interaction ->
				when (interaction) {
					is FocusInteraction.Focus -> count++
					is FocusInteraction.Unfocus -> count--
				}
				val now = count > 0
				if (now != focused) {
					focused = now
					invalidateDraw()
				}
			}
		}
	}

	override fun ContentDrawScope.draw() {
		drawContent()
		if (!focused) return
		val width = ReaderSize.focusRing.toPx()
		val inset = ReaderSize.focusOffset.toPx() + width / 2
		drawRect(
			color = color,
			topLeft = Offset(-inset, -inset),
			size = Size(size.width + inset * 2, size.height + inset * 2),
			style = Stroke(width),
		)
	}
}
