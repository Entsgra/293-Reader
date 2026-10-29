package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.database.entity.BookEntity
import com.entsgra.reader.core.database.entity.FolderEntity
import com.entsgra.reader.core.database.entity.asModel
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.security.Keyring
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A book's identifying details: what a locked folder seals. */
data class BookDetails(val title: String, val author: String?, val contentHash: String)

/** The book columns for [BookDetails]: plain with [sealed] null, or empty beside the sealed blob. */
internal class StoredDetails(val title: String, val author: String?, val contentHash: String, val sealed: ByteArray?)

/**
 * Seals and unseals titles, authors, content hashes and folder names under locked folders'
 * keys. Book details are bound to the book's random file name, folder names to the folder id,
 * so a sealed value copied onto another row fails to open.
 */
@Singleton
class Sealer @Inject constructor(private val keyring: Keyring) {
	/** The book with its details, or null while the key it is under is locked. */
	fun book(entity: BookEntity): Book? {
		val details = details(entity) ?: return null
		return entity.asModel(details.title, details.author, details.contentHash)
	}

	fun details(entity: BookEntity): BookDetails? {
		val key = entity.keyFolderId ?: return BookDetails(entity.title, entity.author, entity.contentHash)
		val keys = keyring[key] ?: return null
		val sealed = entity.sealed ?: return BookDetails(entity.title, entity.author, entity.contentHash)
		return runCatching { decode(keys.open(sealed, bookContext(entity.fileName))) }.getOrNull()
	}

	/** Throws FolderLockedException when [keyFolderId] is locked. */
	internal fun store(details: BookDetails, fileName: String, keyFolderId: Long?): StoredDetails {
		if (keyFolderId == null) return StoredDetails(details.title, details.author, details.contentHash, null)
		val sealed = keyring.require(keyFolderId).seal(encode(details), bookContext(fileName))
		return StoredDetails(title = "", author = null, contentHash = "", sealed = sealed)
	}

	/** The folder's name, or null while the key it is sealed under is locked. */
	fun folderName(entity: FolderEntity): String? {
		val key = entity.keyFolderId ?: return entity.name
		val keys = keyring[key] ?: return null
		val sealed = entity.sealedName ?: return entity.name
		return runCatching { String(keys.open(sealed, folderContext(entity.id)), Charsets.UTF_8) }.getOrNull()
	}

	/** Stored name and sealed name for [name] under [keyFolderId]. */
	internal fun storeName(folderId: Long, name: String, keyFolderId: Long?): Pair<String, ByteArray?> {
		if (keyFolderId == null) return name to null
		return "" to keyring.require(keyFolderId).seal(name.toByteArray(Charsets.UTF_8), folderContext(folderId))
	}

	private fun bookContext(fileName: String) = "293-reader/book/v1/$fileName".toByteArray()

	private fun folderContext(folderId: Long) = "293-reader/folder-name/v1/$folderId".toByteArray()

	internal companion object {
		private const val VERSION = 1

		fun encode(details: BookDetails): ByteArray {
			val out = ByteArrayOutputStream()
			DataOutputStream(out).use { data ->
				data.writeByte(VERSION)
				data.writeString(details.title)
				data.writeBoolean(details.author != null)
				details.author?.let { data.writeString(it) }
				data.writeString(details.contentHash)
			}
			return out.toByteArray()
		}

		fun decode(bytes: ByteArray): BookDetails {
			val data = DataInputStream(bytes.inputStream())
			check(data.readUnsignedByte() == VERSION) { "Unknown sealed details version" }
			val title = data.readString()
			val author = if (data.readBoolean()) data.readString() else null
			return BookDetails(title, author, data.readString())
		}

		private fun DataOutputStream.writeString(value: String) {
			val bytes = value.toByteArray(Charsets.UTF_8)
			writeInt(bytes.size)
			write(bytes)
		}

		private fun DataInputStream.readString(): String {
			val bytes = ByteArray(readInt()).also(::readFully)
			return String(bytes, Charsets.UTF_8)
		}
	}
}
