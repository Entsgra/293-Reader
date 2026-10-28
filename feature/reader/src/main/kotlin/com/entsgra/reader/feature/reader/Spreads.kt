package com.entsgra.reader.feature.reader

/**
 * Groups pages into what one screen shows. Single: one page each. Double: the cover alone, as
 * in print, then facing pairs 2–3, 4–5 and so on; a last odd page stands alone.
 */
internal fun spreadsOf(pageCount: Int, double: Boolean): List<IntRange> {
	if (pageCount <= 0) return emptyList()
	if (!double) return (0 until pageCount).map { it..it }
	return buildList {
		add(0..0)
		var first = 1
		while (first < pageCount) {
			add(first..minOf(first + 1, pageCount - 1))
			first += 2
		}
	}
}

/** The spread holding [page], or the nearest one when [page] is out of range. */
internal fun List<IntRange>.indexOfPage(page: Int): Int {
	if (isEmpty()) return 0
	val index = indexOfFirst { page in it }
	return if (index >= 0) index else if (page < first().first) 0 else lastIndex
}
