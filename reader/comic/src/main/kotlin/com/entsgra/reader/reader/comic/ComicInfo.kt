package com.entsgra.reader.reader.comic

import android.util.Xml
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.reader.api.BookMetadata
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser

/** The fields of ComicRack's ComicInfo.xml that the library uses. */
internal data class ComicInfo(
	val title: String?,
	val series: String?,
	val number: String?,
	val writer: String?,
	val manga: String?,
) {
	/** The issue title, else "Series 12". "YesAndRightToLeft" marks a right-to-left manga. */
	fun toMetadata() = BookMetadata(
		title = title?.takeIf { it.isNotBlank() }
			?: series?.takeIf { it.isNotBlank() }?.let { s -> listOfNotNull(s, number?.takeIf { it.isNotBlank() }).joinToString(" ") },
		author = writer?.takeIf { it.isNotBlank() },
		readingDirection = if (manga.equals("YesAndRightToLeft", ignoreCase = true)) ReadingDirection.RTL else null,
	)

	companion object {
		fun parse(bytes: ByteArray): ComicInfo {
			val parser = Xml.newPullParser().apply {
				// No DTDs or entities from inside the archive.
				setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
				setInput(ByteArrayInputStream(bytes), null)
			}
			val fields = mutableMapOf<String, String>()
			var depth = 0
			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				when (parser.eventType) {
					XmlPullParser.START_TAG -> {
						depth++
						// Direct children of <ComicInfo> only.
						if (depth == 2 && parser.name in FIELDS) {
							val name = parser.name
							val text = parser.nextText().trim()
							depth--
							fields[name] = text
						}
					}
					XmlPullParser.END_TAG -> depth--
				}
			}
			return ComicInfo(
				title = fields["Title"],
				series = fields["Series"],
				number = fields["Number"],
				writer = fields["Writer"],
				manga = fields["Manga"],
			)
		}

		private val FIELDS = setOf("Title", "Series", "Number", "Writer", "Manga")
	}
}
