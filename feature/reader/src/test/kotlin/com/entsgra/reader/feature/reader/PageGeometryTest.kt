package com.entsgra.reader.feature.reader

import androidx.compose.ui.unit.IntSize
import com.entsgra.reader.reader.api.PageSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageGeometryTest {
	@Test
	fun tallPageFitsTheHeight() {
		assertThat(fit(PageSize(600, 900), IntSize(1080, 1200))).isEqualTo(IntSize(800, 1200))
	}

	@Test
	fun widePageFitsTheWidth() {
		assertThat(fit(PageSize(1200, 600), IntSize(1080, 2000))).isEqualTo(IntSize(1080, 540))
	}

	@Test
	fun renderSizeFollowsZoom() {
		assertThat(renderSize(IntSize(1000, 1500), 2f)).isEqualTo(IntSize(2000, 3000))
	}

	@Test
	fun renderSizeIsCappedBySide() {
		val size = renderSize(IntSize(1000, 1500), 5f)
		assertThat(maxOf(size.width, size.height)).isAtMost(4096)
		assertThat(size.width.toFloat() / size.height).isWithin(0.01f).of(1000f / 1500f)
	}

	@Test
	fun renderSizeIsCappedByPixels() {
		val size = renderSize(IntSize(3000, 3000), 5f)
		assertThat(size.width.toLong() * size.height).isAtMost(16_100_000L)
	}

	@Test
	fun renderSizeNeverShrinksBelowTheScreen() {
		assertThat(renderSize(IntSize(1000, 1500), 0.5f)).isEqualTo(IntSize(1000, 1500))
	}
}
