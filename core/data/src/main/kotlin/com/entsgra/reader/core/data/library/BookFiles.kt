package com.entsgra.reader.core.data.library

import android.content.Context
import com.entsgra.reader.core.database.dao.StoredFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where encrypted book and cover files live: no-backup app storage, under random names that
 * say nothing about the book. All methods block; call from a background dispatcher.
 */
@Singleton
class BookFiles @Inject constructor(@ApplicationContext context: Context) {
	private val books = File(context.noBackupFilesDir, "books")
	private val covers = File(context.noBackupFilesDir, "covers")
	private val random = SecureRandom()

	fun book(name: String) = File(books, name)

	fun cover(name: String) = File(covers, name)

	fun newBookFile(): File = File(books.also { it.mkdirs() }, randomName())

	fun newCoverFile(): File = File(covers.also { it.mkdirs() }, randomName())

	fun delete(files: StoredFiles) {
		book(files.fileName).delete()
		files.coverFileName?.let { cover(it).delete() }
	}

	/**
	 * Deletes temp files and anything the database does not reference: what a crash between
	 * writing a file and committing its row leaves behind. Call with imports paused.
	 */
	fun sweep(referenced: Set<String>) {
		listOf(books, covers).forEach { dir ->
			dir.listFiles()?.forEach { file ->
				if (file.name !in referenced) file.delete()
			}
		}
	}

	private fun randomName(): String {
		val bytes = ByteArray(16).also(random::nextBytes)
		return bytes.joinToString("") { "%02x".format(it) }
	}
}
