package com.entsgra.reader.reader.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.core.graphics.scale
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookMetadata
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.PagedDocument
import com.entsgra.reader.reader.archive.Archive
import com.entsgra.reader.reader.archive.ArchiveEntry
import com.entsgra.reader.reader.archive.NaturalOrder
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A comic archive: every image is a page, in natural name order. Archive reads are serialised
 * by [archiveLock]; decoding runs outside it, so the next page can be read while this one decodes.
 * Compressed page bytes are cached, which keeps paging back through a solid 7z cheap.
 */
internal class ComicDocument private constructor(
	private val archive: Archive,
	private val pages: List<ArchiveEntry>,
	override val metadata: BookMetadata,
	private val pool: BitmapPool,
) : PagedDocument {
	private val archiveLock = Mutex()
	private val sizes = arrayOfNulls<PageSize>(pages.size)
	private val encoded = object : LruCache<Int, ByteArray>(ENCODED_CACHE_BYTES) {
		override fun sizeOf(key: Int, value: ByteArray) = value.size
	}

	@Volatile
	private var closed = false

	override val pageCount: Int = pages.size

	override suspend fun pageSize(index: Int): PageSize {
		sizes[index]?.let { return it }
		val bytes = bytes(index)
		return withContext(Dispatchers.Default) { boundsOf(bytes) }.also { sizes[index] = it }
	}

	override suspend fun render(index: Int, target: Bitmap) {
		val bytes = bytes(index)
		val size = pageSize(index)
		withContext(Dispatchers.Default) {
			val sample = sampleSize(size, target.width, target.height)
			val scratch = pool.acquire(ceilDiv(size.width, sample), ceilDiv(size.height, sample))
			try {
				val decoded = decode(bytes, sample, scratch)
				// Transparent PNG pages sit on white paper, like print.
				target.eraseColor(Color.WHITE)
				Canvas(target).drawBitmap(decoded, null, Rect(0, 0, target.width, target.height), SCALE_PAINT)
				if (decoded !== scratch) decoded.recycle()
			} finally {
				pool.release(scratch)
			}
		}
	}

	override suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap? {
		val bytes = bytes(0)
		return withContext(Dispatchers.Default) {
			val size = boundsOf(bytes)
			val scale = minOf(maxWidth / size.width.toFloat(), maxHeight / size.height.toFloat(), 1f)
			val width = max(1, (size.width * scale).roundToInt())
			val height = max(1, (size.height * scale).roundToInt())
			val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(size, width, height) }
			val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
				?: throw BookOpenException(BookOpenException.Reason.DAMAGED)
			if (decoded.width == width && decoded.height == height) {
				decoded
			} else {
				decoded.scale(width, height).also { decoded.recycle() }
			}
		}
	}

	/** Blocks until a read in flight finishes, so call it off the main thread. */
	override fun close() {
		closed = true
		encoded.evictAll()
		runBlocking { archiveLock.withLock { archive.close() } }
	}

	private suspend fun bytes(index: Int): ByteArray {
		encoded.get(index)?.let { return it }
		return archiveLock.withLock {
			encoded.get(index) ?: withContext(Dispatchers.IO) {
				check(!closed) { "Document closed" }
				archive.read(pages[index])
			}.also { encoded.put(index, it) }
		}
	}

	/** Decodes into [scratch]; falls back to a fresh bitmap for codecs that cannot reuse one. */
	private fun decode(bytes: ByteArray, sample: Int, scratch: Bitmap): Bitmap {
		val options = BitmapFactory.Options().apply {
			inSampleSize = sample
			inMutable = true
			inBitmap = scratch
		}
		val decoded = try {
			BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
		} catch (_: IllegalArgumentException) {
			options.inBitmap = null
			BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
		}
		return decoded ?: throw BookOpenException(BookOpenException.Reason.DAMAGED)
	}

	companion object {
		/** Compressed pages kept in memory; a few dozen typical comic pages. */
		private const val ENCODED_CACHE_BYTES = 24 * 1024 * 1024

		private val SCALE_PAINT = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

		private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")

		/** Reads the page list and ComicInfo.xml. Takes ownership of [archive]. */
		fun open(archive: Archive, pool: BitmapPool): ComicDocument {
			try {
				val pages = archive.entries.filter(::isPage).sortedWith(compareBy(NaturalOrder) { it.name })
				if (pages.isEmpty()) throw BookOpenException(BookOpenException.Reason.EMPTY)
				val info = archive.entries
					.firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
					?.let { runCatching { ComicInfo.parse(archive.read(it)) }.getOrNull() }
				return ComicDocument(archive, pages, info?.toMetadata() ?: BookMetadata(null, null), pool)
			} catch (e: Exception) {
				archive.close()
				throw e
			}
		}

		/** Images only, skipping macOS resource forks and hidden files. */
		internal fun isPage(entry: ArchiveEntry): Boolean {
			val segments = entry.name.split('/')
			if (segments.any { it.startsWith('.') || it == "__MACOSX" }) return false
			return segments.last().substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
		}

		/** Largest power of two that keeps the decoded image at least [width] by [height]. */
		internal fun sampleSize(size: PageSize, width: Int, height: Int): Int {
			var sample = 1
			while (size.width / (sample * 2) >= width && size.height / (sample * 2) >= height) sample *= 2
			return sample
		}

		private fun boundsOf(bytes: ByteArray): PageSize {
			val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
			if (options.outWidth <= 0 || options.outHeight <= 0) {
				throw BookOpenException(BookOpenException.Reason.DAMAGED)
			}
			return PageSize(options.outWidth, options.outHeight)
		}

		private fun ceilDiv(value: Int, divisor: Int) = (value + divisor - 1) / divisor
	}
}
