package com.entsgra.reader.reader.epub

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/** Parses with kxml2, the library Android's own pull parser comes from. Call before parsing. */
internal fun useKxml() {
	newPullParser = ::AndroidKxml
}

/**
 * kxml2 never reads DTDs but, unlike Android's copy, refuses to be told so. Accept the "off"
 * setting Android takes, so the code under test runs unchanged.
 */
private class AndroidKxml : KXmlParser() {
	override fun setFeature(feature: String, value: Boolean) {
		if (feature == XmlPullParser.FEATURE_PROCESS_DOCDECL && !value) return
		super.setFeature(feature, value)
	}
}
