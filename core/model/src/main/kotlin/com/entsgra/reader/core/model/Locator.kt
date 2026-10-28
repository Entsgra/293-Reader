package com.entsgra.reader.core.model

/**
 * A position inside a book, independent of format.
 *
 * Paged formats (PDF, comics) use [Page]. Flowing formats (EPUB, TXT, later MOBI/FB2) use [Text]:
 * a spine item and a character offset into it, so the position survives font and margin changes.
 */
sealed interface Locator {
	data class Page(val index: Int) : Locator
	data class Text(val chapter: Int, val offset: Int) : Locator

	fun encode(): String = when (this) {
		is Page -> "p:$index"
		is Text -> "t:$chapter:$offset"
	}

	companion object {
		fun decode(value: String): Locator? {
			val parts = value.split(':')
			return when {
				parts.size == 2 && parts[0] == "p" -> parts[1].toIntOrNull()?.let(::Page)
				parts.size == 3 && parts[0] == "t" -> {
					val chapter = parts[1].toIntOrNull() ?: return null
					val offset = parts[2].toIntOrNull() ?: return null
					Text(chapter, offset)
				}
				else -> null
			}
		}
	}
}
