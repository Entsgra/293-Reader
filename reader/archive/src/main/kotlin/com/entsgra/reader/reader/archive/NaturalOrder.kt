package com.entsgra.reader.reader.archive

/**
 * Orders names the way people number pages: "page2" before "page10", case ignored. Runs of
 * digits compare by value; everything else compares by character.
 */
object NaturalOrder : Comparator<String> {
	override fun compare(a: String, b: String): Int {
		var i = 0
		var j = 0
		while (i < a.length && j < b.length) {
			val ca = a[i]
			val cb = b[j]
			if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
				val endA = a.digitRunEnd(i)
				val endB = b.digitRunEnd(j)
				val byValue = compareDigits(a.substring(i, endA), b.substring(j, endB))
				if (byValue != 0) return byValue
				i = endA
				j = endB
			} else {
				val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
				if (byChar != 0) return byChar
				i++
				j++
			}
		}
		val byLength = (a.length - i).compareTo(b.length - j)
		return if (byLength != 0) byLength else a.compareTo(b)
	}

	/** Compares digit strings by value without overflowing: leading zeros, then length, then text. */
	private fun compareDigits(x: String, y: String): Int {
		val tx = x.trimStart('0')
		val ty = y.trimStart('0')
		if (tx.length != ty.length) return tx.length.compareTo(ty.length)
		val byText = tx.compareTo(ty)
		return if (byText != 0) byText else x.length.compareTo(y.length)
	}

	private fun Char.isAsciiDigit() = this in '0'..'9'

	private fun String.digitRunEnd(start: Int): Int {
		var end = start
		while (end < length && this[end].isAsciiDigit()) end++
		return end
	}
}
