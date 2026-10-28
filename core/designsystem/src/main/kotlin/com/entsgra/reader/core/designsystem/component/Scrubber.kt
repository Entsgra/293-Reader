package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import kotlin.math.roundToInt

/**
 * Picks one of [count] positions, such as a page, along a `rib` track. The part behind the
 * knob fills `action`; the knob is a small `action` disc. [onValueChange] follows the finger;
 * [onValueChangeFinished] fires when it lifts. [reverse] runs the track right to left, for
 * right-to-left books. `target-min` tall for the hit area.
 */
@Composable
fun ReaderScrubber(
	value: Int,
	count: Int,
	onValueChange: (Int) -> Unit,
	onValueChangeFinished: () -> Unit,
	stateDescription: String,
	modifier: Modifier = Modifier,
	reverse: Boolean = false,
) {
	val colors = ReaderTheme.colors
	val last = (count - 1).coerceAtLeast(0)
	val onChange by rememberUpdatedState(onValueChange)
	val onFinished by rememberUpdatedState(onValueChangeFinished)

	fun valueAt(x: Float, width: Int): Int {
		if (width <= 0 || last == 0) return 0
		val fraction = (x / width).coerceIn(0f, 1f)
		return ((if (reverse) 1f - fraction else fraction) * last).roundToInt()
	}

	Canvas(
		modifier
			.fillMaxWidth()
			.height(ReaderSize.targetMin)
			.semantics {
				this.stateDescription = stateDescription
				progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0))
				setProgress { target ->
					onChange(target.roundToInt().coerceIn(0, last))
					onFinished()
					true
				}
			}
			.pointerInput(last, reverse) {
				detectTapGestures { offset ->
					onChange(valueAt(offset.x, size.width))
					onFinished()
				}
			}
			.pointerInput(last, reverse) {
				detectHorizontalDragGestures(
					onDragStart = { onChange(valueAt(it.x, size.width)) },
					onDragEnd = { onFinished() },
					onDragCancel = { onFinished() },
				) { change, _ ->
					change.consume()
					onChange(valueAt(change.position.x, size.width))
				}
			},
	) {
		val knob = ReaderSize.switchKnob.toPx()
		val inset = knob / 2
		val track = size.width - knob
		val y = size.height / 2
		val fraction = if (last == 0) 0f else value.coerceIn(0, last) / last.toFloat()
		val knobX = inset + track * (if (reverse) 1f - fraction else fraction)
		val stroke = ReaderSize.heavyRule.toPx()
		drawLine(colors.rib, Offset(inset, y), Offset(inset + track, y), stroke)
		val readFrom = if (reverse) inset + track else inset
		drawLine(colors.action, Offset(readFrom, y), Offset(knobX, y), stroke)
		drawCircle(colors.action, radius = knob / 2, center = Offset(knobX, y))
	}
}

