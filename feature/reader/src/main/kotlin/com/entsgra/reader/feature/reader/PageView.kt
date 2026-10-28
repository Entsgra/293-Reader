package com.entsgra.reader.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.PagedDocument
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One screen of pages: a single page, or two facing pages meeting at the spine. In a
 * right-to-left book the lower page number sits on the right.
 */
@Composable
internal fun SpreadView(
	document: PagedDocument,
	pages: IntRange,
	rightToLeft: Boolean,
	pool: BitmapPool,
	zoom: ZoomState,
	onTap: (x: Float, width: Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	BoxWithConstraints(
		modifier
			.fillMaxSize()
			.clipToBounds()
			.zoomable(zoom) { offset, size -> onTap(offset.x, size.width) },
		contentAlignment = Alignment.Center,
	) {
		val slots = pages.count()
		val slot = IntSize(constraints.maxWidth / slots, constraints.maxHeight)
		val ordered = if (rightToLeft) pages.reversed() else pages.toList()
		Row(
			Modifier.graphicsLayer { zoom.applyTo(this) },
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically,
		) {
			ordered.forEach { index ->
				PageImage(document, index, slot, zoom.settledScale, pool)
			}
		}
	}
}

/** A page fitted into [slot], rendered at [renderScale] times its on-screen size for zoom. */
@Composable
private fun PageImage(
	document: PagedDocument,
	index: Int,
	slot: IntSize,
	renderScale: Float,
	pool: BitmapPool,
) {
	val density = LocalDensity.current
	val size by produceState<PageSize?>(null, document, index) {
		value = runCatching { document.pageSize(index) }.getOrNull()
	}
	val fitted = size?.let { fit(it, slot) } ?: slot
	Box(Modifier.size(with(density) { fitted.width.toDp() }, with(density) { fitted.height.toDp() })) {
		if (size != null) {
			val bitmap = rememberRenderedPage(document, index, renderSize(fitted, renderScale), pool)
			if (bitmap != null) {
				val image = remember(bitmap) { bitmap.asImageBitmap() }
				Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
			}
		}
	}
}

/**
 * Renders page [index] at [size] into a pooled bitmap. The previous bitmap stays on screen until
 * the new one is ready, then goes back to the pool a frame later, once nothing draws it.
 */
@Composable
private fun rememberRenderedPage(document: PagedDocument, index: Int, size: IntSize, pool: BitmapPool): Bitmap? {
	var shown by remember(document, index) { mutableStateOf<Bitmap?>(null) }
	LaunchedEffect(document, index, size) {
		val fresh = pool.acquire(size.width, size.height)
		try {
			document.render(index, fresh)
		} catch (e: CancellationException) {
			pool.release(fresh)
			throw e
		} catch (_: Exception) {
			// A broken page stays blank; the rest of the book still reads.
			pool.release(fresh)
			return@LaunchedEffect
		}
		val old = shown
		shown = fresh
		if (old != null) {
			try {
				withFrameNanos {}
			} finally {
				pool.release(old)
			}
		}
	}
	DisposableEffect(document, index) {
		onDispose {
			shown?.let(pool::release)
			shown = null
		}
	}
	return shown
}

/** [page] scaled to the largest size that fits inside [slot], aspect kept. */
internal fun fit(page: PageSize, slot: IntSize): IntSize {
	if (page.width <= 0 || page.height <= 0) return slot
	val scale = min(slot.width / page.width.toFloat(), slot.height / page.height.toFloat())
	return IntSize(max(1, (page.width * scale).roundToInt()), max(1, (page.height * scale).roundToInt()))
}

/**
 * The bitmap size for a page shown at [fitted] and zoomed by [scale]. Capped so one page stays
 * under [MAX_RENDER_PIXELS] and [MAX_RENDER_SIDE]: past that, zoom shows scaled pixels.
 */
internal fun renderSize(fitted: IntSize, scale: Float): IntSize {
	var factor = scale.coerceIn(1f, ZoomState.MAX_SCALE)
	val pixels = fitted.width.toFloat() * fitted.height * factor * factor
	if (pixels > MAX_RENDER_PIXELS) factor *= sqrt(MAX_RENDER_PIXELS / pixels)
	val longSide = max(fitted.width, fitted.height) * factor
	if (longSide > MAX_RENDER_SIDE) factor *= MAX_RENDER_SIDE / longSide
	return IntSize(
		max(1, (fitted.width * factor).roundToInt()),
		max(1, (fitted.height * factor).roundToInt()),
	)
}

/** About 64 MB of ARGB per page at most. */
private const val MAX_RENDER_PIXELS = 16_000_000f

/** Within the GPU texture limit of every supported device. */
private const val MAX_RENDER_SIDE = 4096f
