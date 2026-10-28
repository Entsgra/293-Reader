package com.entsgra.reader.reader.api

import com.entsgra.reader.core.model.BookFormatId

/**
 * One file format. Each format lives in its own `reader:*` module and registers a [BookFormat].
 * Adding MOBI, AZW3 or FB2 later means a new module implementing this, nothing else.
 */
interface BookFormat {
	val id: BookFormatId

	/** Cheap check on the first bytes of the plaintext and the original file name. */
	fun canOpen(header: ByteArray, fileName: String): Boolean

	/** Opens a document. Called off the main thread. */
	suspend fun open(source: BookSource): BookDocument
}

/** Picks the [BookFormat] for a file. Built by DI from every registered format. */
class BookFormatRegistry(private val formats: Set<BookFormat>) {
	/** Formats that have a reader. */
	val ids: Set<BookFormatId> = formats.mapTo(mutableSetOf()) { it.id }

	fun forId(id: BookFormatId): BookFormat? = formats.firstOrNull { it.id == id }

	fun canRead(id: BookFormatId): Boolean = id in ids

	fun detect(header: ByteArray, fileName: String): BookFormat? =
		formats.firstOrNull { it.canOpen(header, fileName) }
}
