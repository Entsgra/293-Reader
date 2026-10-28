package com.entsgra.reader.reader.epub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.scale
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.reader.api.BookMetadata
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowDocument
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.TocEntry
import com.entsgra.reader.reader.archive.Archive
import com.entsgra.reader.reader.archive.ArchiveEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An EPUB read in place from its zip: chapters, images and the cover are pulled from the
 * decrypting archive when asked for, never extracted. Parsed chapters are kept for the few
 * the reader is likely to come back to.
 */
internal class EpubDocument private constructor(
	private val archive: Archive,
	private val entries: Map<String, ArchiveEntry>,
	private val pkg: EpubPackage,
	override val contents: List<TocEntry>,
) : FlowDocument {
	/** The archive reads through one channel; one reader at a time. */
	private val archiveLock = Mutex()
	private var closed = false

	private val chapters = LruCache<Int, FlowContent>(CACHED_CHAPTERS)
	private val imageSizes = LruCache<String, PageSize>(CACHED_IMAGE_SIZES)
	private val chapterIndex: Map<String, Int> = pkg.spine.withIndex().associate { (i, item) -> item.path to i }

	override val metadata = BookMetadata(
		title = pkg.title,
		author = pkg.author,
		readingDirection = if (pkg.rightToLeft) ReadingDirection.RTL else null,
	)

	override val chapterCount: Int = pkg.spine.size

	override val chapterWeights: List<Long> = pkg.spine.map { item ->
		if (item.image) IMAGE_WEIGHT else (find(item.path)?.size ?: 0L).coerceAtLeast(1L)
	}

	override val language: String? = pkg.language

	override suspend fun chapter(index: Int): FlowContent {
		chapters.get(index)?.let { return it }
		val item = pkg.spine[index]
		val content = if (item.image) {
			FlowContent(listOf(FlowBlock.Image(item.path, null)))
		} else {
			// A spine item missing from the zip reads as an empty chapter rather than failing the book.
			val bytes = read(item.path) ?: return FlowContent(emptyList())
			withContext(Dispatchers.Default) { XhtmlParser(item.path, chapterIndex::get).parse(bytes) }
		}
		chapters.put(index, content)
		return content
	}

	override suspend fun imageSize(key: String): PageSize? {
		imageSizes.get(key)?.let { return it }
		val bytes = read(key) ?: return null
		val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
		if (options.outWidth <= 0 || options.outHeight <= 0) return null
		return PageSize(options.outWidth, options.outHeight).also { imageSizes.put(key, it) }
	}

	override suspend fun image(key: String, maxWidth: Int, maxHeight: Int): Bitmap? {
		val bytes = read(key) ?: return null
		return withContext(Dispatchers.Default) { decodeToFit(bytes, maxWidth, maxHeight) }
	}

	override suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap? {
		val path = pkg.coverPath ?: pkg.spine.firstOrNull { it.image }?.path ?: return null
		return image(path, maxWidth, maxHeight)
	}

	/** Waits for any read in flight, so call it off the main thread. */
	override fun close() = runBlocking {
		archiveLock.withLock {
			if (!closed) {
				closed = true
				archive.close()
			}
		}
	}

	private fun find(path: String): ArchiveEntry? = entries[path] ?: entries[path.lowercase()]

	private suspend fun read(path: String): ByteArray? {
		val entry = find(path) ?: return null
		return withContext(Dispatchers.IO) {
			archiveLock.withLock {
				check(!closed) { "Document is closed" }
				archive.read(entry)
			}
		}
	}

	companion object {
		private const val CACHED_CHAPTERS = 4
		private const val CACHED_IMAGE_SIZES = 256

		/** A chapter that is one full-page picture counts like a short page of text. */
		private const val IMAGE_WEIGHT = 2_000L

		/** Reads the package and TOC. Takes ownership of [archive], closing it on failure. */
		fun open(archive: Archive): EpubDocument = try {
			val entries = buildMap {
				// Exact names first; lowercase names as a fallback for hrefs with the wrong case.
				archive.entries.forEach { putIfAbsent(it.name.lowercase(), it) }
				archive.entries.forEach { put(it.name, it) }
			}
			(entries["META-INF/encryption.xml"] ?: entries["meta-inf/encryption.xml"])?.let { encryption ->
				if (EpubPackage.isProtected(archive.read(encryption))) {
					throw BookOpenException(BookOpenException.Reason.PROTECTED)
				}
			}
			val container = entries["META-INF/container.xml"] ?: entries["meta-inf/container.xml"] ?: throw BookOpenException(BookOpenException.Reason.DAMAGED)
			val opfPath = EpubPackage.rootfile(archive.read(container)) ?: throw BookOpenException(BookOpenException.Reason.DAMAGED)
			val opf = entries[opfPath] ?: entries[opfPath.lowercase()] ?: throw BookOpenException(BookOpenException.Reason.DAMAGED)
			val pkg = EpubPackage.parse(opfPath, archive.read(opf))
			EpubDocument(archive, entries, pkg, contentsOf(archive, entries, pkg))
		} catch (e: Exception) {
			archive.close()
			throw when (e) {
				is BookOpenException -> e
				else -> BookOpenException(BookOpenException.Reason.DAMAGED, e)
			}
		}

		/** The nav document's TOC, else the NCX's. A broken TOC leaves the book readable without one. */
		private fun contentsOf(archive: Archive, entries: Map<String, ArchiveEntry>, pkg: EpubPackage): List<TocEntry> {
			val chapterOf = pkg.spine.withIndex().associate { (i, item) -> item.path to i }
			fun load(path: String?, parse: (String, ByteArray) -> List<RawTocEntry>): List<RawTocEntry> {
				val entry = path?.let { entries[it] ?: entries[it.lowercase()] } ?: return emptyList()
				return runCatching { parse(path, archive.read(entry)) }.getOrDefault(emptyList())
			}
			val raw = load(pkg.navPath, EpubToc::fromNav).ifEmpty { load(pkg.ncxPath, EpubToc::fromNcx) }
			return raw.mapNotNull { entry ->
				val chapter = chapterOf[entry.path] ?: return@mapNotNull null
				TocEntry(entry.title, FlowLink(chapter, entry.fragment), entry.depth)
			}
		}

		/** Decodes at the smallest power-of-two reduction that still covers the box. */
		private fun decodeToFit(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
			val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
			if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
			var sample = 1
			while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) sample *= 2
			val options = BitmapFactory.Options().apply {
				inSampleSize = sample
				inPreferredConfig = Bitmap.Config.ARGB_8888
			}
			val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
			val scale = minOf(maxWidth / decoded.width.toFloat(), maxHeight / decoded.height.toFloat())
			if (scale >= 1f) return decoded
			val scaled = decoded.scale(
				(decoded.width * scale).toInt().coerceAtLeast(1),
				(decoded.height * scale).toInt().coerceAtLeast(1),
			)
			if (scaled !== decoded) decoded.recycle()
			return scaled
		}
	}
}
