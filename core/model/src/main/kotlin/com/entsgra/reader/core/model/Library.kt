package com.entsgra.reader.core.model

/** Order of books inside a folder. Folders always sort by name. */
enum class LibrarySort { TITLE, RECENT, ADDED }

/** A folder row: the folder and how many books and folders sit directly inside it. */
data class FolderItem(
	val folder: Folder,
	val itemCount: Int,
)

/** Books and folders in a folder and everything below it. */
data class LibraryCounts(
	val books: Int,
	val folders: Int,
)

/** One entry in the folder picker: a folder and how deep it sits, root children at 0. */
data class FolderNode(
	val folder: Folder,
	val depth: Int,
)
