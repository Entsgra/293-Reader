package com.entsgra.reader.reader.epub

import java.io.ByteArrayOutputStream

/** Resolving hrefs inside an EPUB, where every path is relative to the file that names it. */
internal object Paths {
	/**
	 * The archive path [href] points to from the file at [from], without its fragment.
	 * Percent escapes are decoded, `.` and `..` folded. Null for links out of the book
	 * (`http:`, `mailto:`) and for paths that climb above the archive root.
	 */
	fun resolve(from: String, href: String): String? {
		val path = href.substringBefore('#').substringBefore('?')
		if (SCHEME.containsMatchIn(path)) return null
		val decoded = percentDecode(path)
		val joined = when {
			decoded.startsWith("/") -> decoded.trimStart('/')
			decoded.isEmpty() -> from
			else -> from.substringBeforeLast('/', "").let { if (it.isEmpty()) decoded else "$it/$decoded" }
		}
		val parts = ArrayDeque<String>()
		for (part in joined.split('/')) {
			when (part) {
				"", "." -> Unit
				".." -> parts.removeLastOrNull() ?: return null
				else -> parts.addLast(part)
			}
		}
		return parts.joinToString("/").ifEmpty { null }
	}

	/** The fragment of [href], decoded, or null when it has none. */
	fun fragment(href: String): String? =
		href.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let(::percentDecode)

	/** Decodes `%XX` escapes as UTF-8. Unlike URLDecoder, `+` stays a plus. */
	fun percentDecode(value: String): String {
		if ('%' !in value) return value
		val out = ByteArrayOutputStream(value.length)
		var i = 0
		while (i < value.length) {
			val c = value[i]
			val high = if (c == '%' && i + 2 <= value.lastIndex) value[i + 1].digitToIntOrNull(16) else null
			val low = high?.let { value[i + 2].digitToIntOrNull(16) }
			if (high != null && low != null) {
				out.write(high * 16 + low)
				i += 3
			} else {
				out.write(c.toString().toByteArray(Charsets.UTF_8))
				i++
			}
		}
		return out.toString(Charsets.UTF_8.name())
	}

	/** `http:`, `mailto:`, `data:`: anything with a scheme leaves the book. */
	private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
