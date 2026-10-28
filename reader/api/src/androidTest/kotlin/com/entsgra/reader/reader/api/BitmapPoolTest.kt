package com.entsgra.reader.reader.api

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapPoolTest {
	@Test
	fun reusesAReleasedBitmapOfTheSameSize() {
		val pool = BitmapPool(maxBytes = 64L * 1024 * 1024)
		val first = pool.acquire(400, 600)
		pool.release(first)

		assertThat(pool.acquire(400, 600)).isSameInstanceAs(first)
	}

	@Test
	fun reconfiguresALargerBitmapToTheAskedSize() {
		val pool = BitmapPool(maxBytes = 64L * 1024 * 1024)
		val big = pool.acquire(400, 600)
		pool.release(big)

		val smaller = pool.acquire(380, 600)
		assertThat(smaller).isSameInstanceAs(big)
		assertThat(smaller.width).isEqualTo(380)
		assertThat(smaller.height).isEqualTo(600)
		assertThat(smaller.config).isEqualTo(Bitmap.Config.ARGB_8888)
	}

	@Test
	fun doesNotHandOutABitmapTwiceTheSizeNeeded() {
		val pool = BitmapPool(maxBytes = 64L * 1024 * 1024)
		val huge = pool.acquire(1000, 1000)
		pool.release(huge)

		assertThat(pool.acquire(100, 100)).isNotSameInstanceAs(huge)
	}

	@Test
	fun evictsTheOldestPastTheLimit() {
		val pool = BitmapPool(maxBytes = 100L * 100 * 4)
		val a = pool.acquire(100, 100)
		val b = pool.acquire(100, 100)
		pool.release(a)
		pool.release(b)

		assertThat(a.isRecycled).isTrue()
		assertThat(b.isRecycled).isFalse()
	}

	@Test
	fun releasingTwiceKeepsOneCopy() {
		val pool = BitmapPool(maxBytes = 64L * 1024 * 1024)
		val a = pool.acquire(10, 10)
		pool.release(a)
		pool.release(a)

		assertThat(pool.acquire(10, 10)).isSameInstanceAs(a)
		assertThat(pool.acquire(10, 10)).isNotSameInstanceAs(a)
	}

	@Test
	fun clearRecyclesIdleBitmaps() {
		val pool = BitmapPool(maxBytes = 64L * 1024 * 1024)
		val a = pool.acquire(10, 10)
		pool.release(a)
		pool.clear()

		assertThat(a.isRecycled).isTrue()
	}
}
