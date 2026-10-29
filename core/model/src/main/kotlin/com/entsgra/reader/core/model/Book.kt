package com.entsgra.reader.core.model

/** An ebook imported into app-private, encrypted storage. */
data class Book(
	val id: Long,
	val folderId: Long?,
	val title: String,
	val author: String?,
	val format: BookFormatId,
	val fileName: String,
	val fileSize: Long,
	val contentHash: String,
	val coverFileName: String?,
	val pageCount: Int?,
	val readingDirection: ReadingDirection,
	val spreadMode: SpreadMode,
	val addedAt: Long,
	val lastOpenedAt: Long?,
	/** The locked folder whose key encrypts this book's file and cover; null for the app key. */
	val keyFolderId: Long? = null,
)

/** File formats the reader layer understands. New formats are added here and behind `reader:api`. */
enum class BookFormatId(val extensions: Set<String>) {
	PDF(setOf("pdf")),
	EPUB(setOf("epub")),
	CBZ(setOf("cbz")),
	CB7(setOf("cb7")),
	TXT(setOf("txt")),
	;

	companion object {
		fun fromExtension(extension: String): BookFormatId? {
			val ext = extension.lowercase()
			return entries.firstOrNull { ext in it.extensions }
		}
	}
}

enum class ReadingDirection { LTR, RTL }

enum class SpreadMode { SINGLE, DOUBLE, AUTO }
