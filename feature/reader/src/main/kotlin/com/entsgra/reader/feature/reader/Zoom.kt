package com.entsgra.reader.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * Pinch and double-tap zoom for one screen of pages. [scale] and [offset] follow the fingers;
 * [settledScale] changes only when a gesture ends, and is what pages re-render at, so a pinch
 * does not render a bitmap per frame.
 */
@Stable
internal class ZoomState {
	var scale by mutableFloatStateOf(1f)
		private set
	var offset by mutableStateOf(Offset.Zero)
		private set
	var settledScale by mutableFloatStateOf(1f)
		private set

	val isZoomed: Boolean get() = scale > 1.01f

	/** Zooms by [zoom] about [centroid] and pans by [pan]; both in layout pixels from the top left. */
	fun transform(centroid: Offset, pan: Offset, zoom: Float, size: IntSize) {
		val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
		val center = size.toSize().center
		// Keep the point under the fingers still: content point p shows at center + p * scale + offset.
		val anchor = centroid - center
		val newOffset = anchor - (anchor - offset) * (newScale / scale) + pan
		scale = newScale
		offset = clamp(newOffset, newScale, size)
	}

	/** Double tap: zoom in on [at], or back out to fit. */
	fun toggle(at: Offset, size: IntSize) {
		if (isZoomed) {
			reset()
		} else {
			val anchor = at - size.toSize().center
			scale = DOUBLE_TAP_SCALE
			offset = clamp(-anchor * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE, size)
			settle()
		}
	}

	fun settle() {
		settledScale = scale
	}

	fun reset() {
		scale = 1f
		offset = Offset.Zero
		settledScale = 1f
	}

	fun applyTo(layer: GraphicsLayerScope) {
		layer.scaleX = scale
		layer.scaleY = scale
		layer.translationX = offset.x
		layer.translationY = offset.y
	}

	/** Never pan past the content's edges. */
	private fun clamp(offset: Offset, scale: Float, size: IntSize): Offset {
		val maxX = (scale - 1f) * size.width / 2f
		val maxY = (scale - 1f) * size.height / 2f
		return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
	}

	companion object {
		const val MAX_SCALE = 5f
		const val DOUBLE_TAP_SCALE = 2.5f
	}
}

private val androidx.compose.ui.geometry.Size.center get() = Offset(width / 2f, height / 2f)

/**
 * Taps go to [onTap]; double taps zoom. Two fingers always zoom. One finger pans only while
 * zoomed in; otherwise it is left for the pager to turn the page.
 */
internal fun Modifier.zoomable(state: ZoomState, onTap: (Offset, IntSize) -> Unit): Modifier =
	pointerInput(state) {
		detectTapGestures(
			onDoubleTap = { state.toggle(it, size) },
			onTap = { onTap(it, size) },
		)
	}.pointerInput(state) {
		awaitEachGesture {
			awaitFirstDown(requireUnconsumed = false)
			var transformed = false
			do {
				val event = awaitPointerEvent()
				val fingers = event.changes.count { it.pressed }
				if (fingers >= 2 || state.isZoomed) {
					val zoom = event.calculateZoom()
					val pan = event.calculatePan()
					if (zoom != 1f || pan != Offset.Zero) {
						state.transform(event.calculateCentroid(useCurrent = true), pan, zoom, size)
						transformed = true
					}
					event.changes.forEach { if (it.positionChanged()) it.consume() }
				}
			} while (event.changes.any { it.pressed })
			if (transformed) state.settle()
		}
	}
