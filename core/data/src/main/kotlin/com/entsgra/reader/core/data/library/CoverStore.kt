package com.entsgra.reader.core.data.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.entsgra.reader.core.security.FileCipher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cover thumbnails, encrypted at rest like the books. Decoded once per size into hardware
 * bitmaps (no heap copy) and kept in a small LRU, so scrolling the grid never touches disk
 * for a cover it has shown.
 */
@Singleton
class CoverStore @Inject constructor(
	private val files: BookFiles,
	private val cipher: FileCipher,
) {
	private val memory = object : LruCache<String, Bitmap>(MEMORY_KB) {
		override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
	}

	/** Encodes and encrypts [bitmap] under [keyFolderId]'s key. Returns the stored file name. */
	suspend fun save(bitmap: Bitmap, keyFolderId: Long? = null): String = withContext(Dispatchers.IO) {
		val encoded = ByteArrayOutputStream().also {
			bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it)
		}
		val target = files.newCoverFile()
		cipher.encrypt(ByteArrayInputStream(encoded.toByteArray()), target, keyFolderId)
		target.name
	}

	/**
	 * The cover [name] scaled down to at least [minWidth] pixels wide, or null if unreadable or
	 * under a locked folder's key.
	 */
	suspend fun load(name: String, minWidth: Int, keyFolderId: Long? = null): Bitmap? {
		val key = "$name/$minWidth"
		memory.get(key)?.let { return it }
		return withContext(Dispatchers.IO) {
			runCatching { decode(name, minWidth, keyFolderId) }.getOrNull()?.also { memory.put(key, it) }
		}
	}

	/** Drops decoded covers, for when a folder locks. */
	fun clearMemory() = memory.evictAll()

	private fun decode(name: String, minWidth: Int, keyFolderId: Long?): Bitmap? {
		// Covers are small; read the plaintext once and decode bounds and pixels from memory.
		val bytes = cipher.openInputStream(files.cover(name), keyFolderId).use { it.readBytes() }
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
		var sample = 1
		while (bounds.outWidth / (sample * 2) >= minWidth) sample *= 2
		val options = BitmapFactory.Options().apply {
			inSampleSize = sample
			inPreferredConfig = Bitmap.Config.HARDWARE
		}
		return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
	}

	companion object {
		/** Size formats render covers at before they are stored. */
		const val COVER_WIDTH = 360
		const val COVER_HEIGHT = 540
		private const val QUALITY = 85
		private const val MEMORY_KB = 16 * 1024
	}
}
