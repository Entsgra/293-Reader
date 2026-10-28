package com.entsgra.reader.reader.epub

import android.util.Xml
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * A pull parser for markup from inside a book. DTDs are never processed, so a file cannot pull
 * in external entities. Namespaces are off: names keep their prefix (`epub:type`), and [local]
 * strips it. The HTML entities XHTML files use without declaring are predefined, and relaxed
 * mode keeps any other unknown entity as text instead of failing the whole chapter.
 */
internal fun xmlParser(bytes: ByteArray): XmlPullParser = newPullParser().apply {
	setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
	setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
	runCatching { setFeature(FEATURE_RELAXED, true) }
	setInput(ByteArrayInputStream(bytes), null)
	HTML_ENTITIES.forEach { (name, codePoint) -> defineEntityReplacementText(name, String(Character.toChars(codePoint))) }
}

/** Android's KXml parser. JVM tests, where `android.util.Xml` is a stub, swap in the same parser from kxml2. */
internal var newPullParser: () -> XmlPullParser = { Xml.newPullParser() }

/** The element or attribute name without its namespace prefix, lowercased. */
internal fun local(name: String?): String = name.orEmpty().substringAfterLast(':').lowercase()

/** Attribute [name] of the current start tag, matched without prefix or case. */
internal fun XmlPullParser.attr(name: String): String? {
	for (i in 0 until attributeCount) {
		if (local(getAttributeName(i)) == name) return getAttributeValue(i)
	}
	return null
}

/**
 * All text inside the current start tag, through nested tags, unlike `nextText`, which fails on
 * markup inside. Leaves the parser on the matching end tag.
 */
internal fun XmlPullParser.readText(): String {
	val text = StringBuilder()
	var depth = 1
	while (depth > 0) {
		when (next()) {
			XmlPullParser.START_TAG -> depth++
			XmlPullParser.END_TAG -> depth--
			XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> text.append(this.text)
			XmlPullParser.END_DOCUMENT -> break
		}
	}
	return text.toString()
}

/** Collapses runs of whitespace to one space and trims. */
internal fun String.squeezed(): String = WHITESPACE.replace(this, " ").trim()

private val WHITESPACE = Regex("\\s+")

private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

/** Latin-1 entity names for U+00A0 to U+00FF, in order. */
private const val LATIN_1 = "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr " +
	"deg plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest " +
	"Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml " +
	"ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig " +
	"agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml " +
	"eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml"

private val HTML_ENTITIES: Map<String, Int> = buildMap {
	LATIN_1.split(' ').forEachIndexed { i, name -> put(name, 0xA0 + i) }
	putAll(
		mapOf(
			"OElig" to 338, "oelig" to 339, "Scaron" to 352, "scaron" to 353, "Yuml" to 376, "fnof" to 402,
			"circ" to 710, "tilde" to 732, "ensp" to 8194, "emsp" to 8195, "thinsp" to 8201, "zwnj" to 8204,
			"zwj" to 8205, "ndash" to 8211, "mdash" to 8212, "lsquo" to 8216, "rsquo" to 8217, "sbquo" to 8218,
			"ldquo" to 8220, "rdquo" to 8221, "bdquo" to 8222, "dagger" to 8224, "Dagger" to 8225, "bull" to 8226,
			"hellip" to 8230, "permil" to 8240, "prime" to 8242, "Prime" to 8243, "lsaquo" to 8249,
			"rsaquo" to 8250, "euro" to 8364, "trade" to 8482, "larr" to 8592, "rarr" to 8594, "minus" to 8722,
		),
	)
}
