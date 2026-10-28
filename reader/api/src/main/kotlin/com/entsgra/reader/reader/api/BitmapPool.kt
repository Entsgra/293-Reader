package com.entsgra.reader.reader.api

import android.graphics.Bitmap

/**
 * Reuses page-sized bitmaps so turning pages does not churn native memory. A screen-sized
 * ARGB page is about 10 MB; allocating one per turn makes the GC and the allocator the
 * bottleneck. Bitmaps handed out are owned by the caller until [release]d; idle ones are kept
 * up to [maxBytes], oldest dropped first.
 */
class BitmapPool(private val maxBytes: Long) {
	private val idle = ArrayDeque<Bitmap>()
	private var idleBytes = 0L

	/** A mutable ARGB_8888 bitmap of exactly [width] by [height]. Its pixels are undefined. */
	fun acquire(width: Int, height: Int): Bitmap {
		require(width > 0 && height > 0) { "Empty bitmap $width x $height" }
		val needed = width.toLong() * height * BYTES_PER_PIXEL
		val reused = synchronized(this) {
			// Smallest idle bitmap that fits without wasting more than its own size again.
			idle.filter { it.allocationByteCount in needed..needed * 2 }
				.minByOrNull { it.allocationByteCount }
				?.also {
					idle.remove(it)
					idleBytes -= it.allocationByteCount
				}
		}
		if (reused != null) {
			reused.reconfigure(width, height, Bitmap.Config.ARGB_8888)
			return reused
		}
		return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
	}

	/** Returns [bitmap] for reuse. The caller must not draw or read it afterwards. */
	fun release(bitmap: Bitmap) {
		if (bitmap.isRecycled || !bitmap.isMutable || bitmap.config != Bitmap.Config.ARGB_8888) return
		val evicted = synchronized(this) {
			if (idle.any { it === bitmap }) return
			idle.addLast(bitmap)
			idleBytes += bitmap.allocationByteCount
			trimTo(maxBytes)
		}
		evicted.forEach(Bitmap::recycle)
	}

	/** Frees every idle bitmap, for when the app is backgrounded or memory runs low. */
	fun clear() {
		synchronized(this) { trimTo(0) }.forEach(Bitmap::recycle)
	}

	private fun trimTo(limit: Long): List<Bitmap> {
		val evicted = mutableListOf<Bitmap>()
		while (idleBytes > limit && idle.isNotEmpty()) {
			val oldest = idle.removeFirst()
			idleBytes -= oldest.allocationByteCount
			evicted += oldest
		}
		return evicted
	}

	private companion object {
		const val BYTES_PER_PIXEL = 4
	}
}
