package com.entsgra.reader.reader.epub

import com.entsgra.reader.reader.api.BookOpenException
import org.xmlpull.v1.XmlPullParser

/** What the OPF package file says: metadata, the reading order and where the cover and TOC are. */
internal data class EpubPackage(
	val title: String?,
	val author: String?,
	val language: String?,
	val rightToLeft: Boolean,
	/** Archive paths of the spine items, in reading order. */
	val spine: List<SpineItem>,
	val coverPath: String?,
	/** EPUB 3 navigation document. */
	val navPath: String?,
	/** EPUB 2 NCX. */
	val ncxPath: String?,
) {
	data class SpineItem(val path: String, val image: Boolean)

	companion object {
		/** The package file named by `META-INF/container.xml`. */
		fun rootfile(container: ByteArray): String? {
			val parser = xmlParser(container)
			var fallback: String? = null
			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				if (parser.eventType != XmlPullParser.START_TAG || local(parser.name) != "rootfile") continue
				val path = parser.attr("full-path")?.let { Paths.resolve("", it) } ?: continue
				if (parser.attr("media-type") == OPF_TYPE) return path
				if (fallback == null) fallback = path
			}
			return fallback
		}

		fun parse(opfPath: String, bytes: ByteArray): EpubPackage {
			val parser = xmlParser(bytes)
			val items = mutableMapOf<String, Item>()
			val spineIds = mutableListOf<String>()
			val creators = mutableListOf<String>()
			var title: String? = null
			var language: String? = null
			var coverId: String? = null
			var tocId: String? = null
			var rightToLeft = false
			var inMetadata = false

			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				when (parser.eventType) {
					XmlPullParser.START_TAG -> when (local(parser.name)) {
						"metadata" -> inMetadata = true
						"title" -> if (inMetadata) {
							val text = parser.readText().squeezed()
							if (title == null && text.isNotEmpty()) title = text
						}
						"creator" -> if (inMetadata) {
							val role = parser.attr("role")
							val text = parser.readText().squeezed()
							if (text.isNotEmpty() && (role == null || role == "aut")) creators += text
						}
						"language" -> if (inMetadata) {
							val text = parser.readText().squeezed()
							if (language == null && text.isNotEmpty()) language = text
						}
						"meta" -> if (parser.attr("name") == "cover") coverId = parser.attr("content")
						"item" -> {
							val id = parser.attr("id")
							val path = parser.attr("href")?.let { Paths.resolve(opfPath, it) }
							if (id != null && path != null) {
								items[id] = Item(
									path = path,
									mediaType = parser.attr("media-type").orEmpty().lowercase(),
									properties = parser.attr("properties").orEmpty().split(' ').toSet(),
								)
							}
						}
						"spine" -> {
							tocId = parser.attr("toc")
							rightToLeft = parser.attr("page-progression-direction") == "rtl"
						}
						"itemref" -> parser.attr("idref")?.let(spineIds::add)
					}
					XmlPullParser.END_TAG -> if (local(parser.name) == "metadata") inMetadata = false
				}
			}

			val spine = spineIds.mapNotNull { items[it] }
				.filter { it.isText || it.isImage }
				.map { SpineItem(it.path, image = it.isImage) }
			if (spine.isEmpty()) throw BookOpenException(BookOpenException.Reason.EMPTY)

			val cover = items.values.firstOrNull { "cover-image" in it.properties }
				?: coverId?.let(items::get)?.takeIf { it.isImage }
				?: items.entries.firstOrNull { (id, item) -> item.isImage && "cover" in (id + item.path).lowercase() }?.value
			return EpubPackage(
				title = title,
				author = creators.take(MAX_AUTHORS).joinToString(", ").ifEmpty { null },
				language = language,
				rightToLeft = rightToLeft,
				spine = spine,
				coverPath = cover?.path,
				navPath = items.values.firstOrNull { "nav" in it.properties }?.path,
				ncxPath = (tocId?.let(items::get) ?: items.values.firstOrNull { it.mediaType == NCX_TYPE })?.path,
			)
		}

		/**
		 * True when `META-INF/encryption.xml` encrypts anything with real DRM. Font obfuscation,
		 * the only encryption an unprotected EPUB may carry, does not count.
		 */
		fun isProtected(encryption: ByteArray): Boolean {
			val parser = try {
				xmlParser(encryption)
			} catch (_: Exception) {
				return true
			}
			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				if (parser.eventType == XmlPullParser.START_TAG && local(parser.name) == "encryptionmethod") {
					if (parser.attr("algorithm") !in FONT_OBFUSCATION) return true
				}
			}
			return false
		}

		private const val OPF_TYPE = "application/oebps-package+xml"
		private const val NCX_TYPE = "application/x-dtbncx+xml"
		private const val MAX_AUTHORS = 3

		private val FONT_OBFUSCATION = setOf(
			"http://www.idpf.org/2008/embedding",
			"http://ns.adobe.com/pdf/enc#RC",
		)
	}

	private data class Item(val path: String, val mediaType: String, val properties: Set<String>) {
		val isText get() = mediaType in TEXT_TYPES ||
			(mediaType.isEmpty() && path.substringAfterLast('.').lowercase() in setOf("xhtml", "html", "htm"))
		val isImage get() = mediaType.startsWith("image/") && mediaType != "image/svg+xml"
	}
}

private val TEXT_TYPES = setOf("application/xhtml+xml", "text/html", "application/x-dtbook+xml")

/**
 * The table of contents from an EPUB 3 nav document or an EPUB 2 NCX, as titles and hrefs
 * already resolved to archive paths. [depth] starts at 0.
 */
internal data class RawTocEntry(val title: String, val path: String, val fragment: String?, val depth: Int)

internal object EpubToc {
	/** Entries of the `toc` nav; the first nav when none is marked. */
	fun fromNav(navPath: String, bytes: ByteArray): List<RawTocEntry> {
		val parser = xmlParser(bytes)
		val entries = mutableListOf<RawTocEntry>()
		var navDepth = 0
		var listDepth = 0
		var inToc = false
		var tocSeen = false
		while (parser.next() != XmlPullParser.END_DOCUMENT) {
			when (parser.eventType) {
				XmlPullParser.START_TAG -> when (local(parser.name)) {
					"nav" -> {
						navDepth++
						if (navDepth == 1) {
							val types = parser.attr("type").orEmpty().split(' ')
							when {
								"toc" in types && !tocSeen -> {
									// A marked toc replaces anything taken from an unmarked nav before it.
									entries.clear()
									inToc = true
									tocSeen = true
								}
								types == listOf("") && !tocSeen && entries.isEmpty() -> inToc = true
							}
						}
					}
					"ol", "ul" -> if (inToc) listDepth++
					"a" -> if (inToc) {
						val href = parser.attr("href")
						val title = parser.readText().squeezed()
						val path = href?.let { Paths.resolve(navPath, it) }
						if (path != null && title.isNotEmpty()) {
							entries += RawTocEntry(title, path, Paths.fragment(href), (listDepth - 1).coerceAtLeast(0))
						}
					}
				}
				XmlPullParser.END_TAG -> when (local(parser.name)) {
					"nav" -> {
						navDepth--
						if (navDepth == 0) inToc = false
					}
					"ol", "ul" -> if (inToc) listDepth--
				}
			}
		}
		return entries
	}

	fun fromNcx(ncxPath: String, bytes: ByteArray): List<RawTocEntry> {
		val parser = xmlParser(bytes)
		val entries = mutableListOf<RawTocEntry>()
		var depth = 0
		var label: String? = null
		var inNavMap = false
		while (parser.next() != XmlPullParser.END_DOCUMENT) {
			when (parser.eventType) {
				XmlPullParser.START_TAG -> when (local(parser.name)) {
					"navmap" -> inNavMap = true
					"navpoint" -> if (inNavMap) {
						depth++
						label = null
					}
					"text" -> if (inNavMap && label == null) label = parser.readText().squeezed()
					"content" -> if (inNavMap) {
						val src = parser.attr("src")
						val path = src?.let { Paths.resolve(ncxPath, it) }
						val title = label
						if (path != null && !title.isNullOrEmpty()) {
							entries += RawTocEntry(title, path, Paths.fragment(src), (depth - 1).coerceAtLeast(0))
						}
					}
				}
				XmlPullParser.END_TAG -> when (local(parser.name)) {
					"navmap" -> inNavMap = false
					"navpoint" -> if (inNavMap) depth--
				}
			}
		}
		return entries
	}
}
