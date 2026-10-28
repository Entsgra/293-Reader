package com.entsgra.reader.feature.library

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.data.library.BookImporter
import com.entsgra.reader.core.data.library.CoverStore
import com.entsgra.reader.core.data.library.ImportState
import com.entsgra.reader.core.data.library.LibraryRepository
import com.entsgra.reader.core.data.library.NameCheck
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderItem
import com.entsgra.reader.core.model.FolderNode
import com.entsgra.reader.core.model.LibraryCounts
import com.entsgra.reader.core.model.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
	private val savedState: SavedStateHandle,
	private val library: LibraryRepository,
	private val importer: BookImporter,
	private val settings: SettingsRepository,
	private val covers: CoverStore,
) : ViewModel() {
	val folderId: Long? = savedState.toRoute<LibraryRoute>().folderId

	/** This folder; null at the root, and null once the folder is gone. */
	val folder: StateFlow<Folder?> = (folderId?.let(library::folder) ?: flowOf(null))
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

	/** True when this screen's folder was deleted or moved out from under it. */
	val folderGone: StateFlow<Boolean> = (folderId?.let { id -> library.folder(id).map { it == null } } ?: flowOf(false))
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), false)

	val folders: StateFlow<List<FolderItem>> = library.folders(folderId)
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

	/** Null until loaded, so the screen never flashes an empty state. */
	val counts: StateFlow<LibraryCounts?> = library.counts(folderId)
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

	val sort: StateFlow<LibrarySort> = settings.librarySort
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), LibrarySort.TITLE)

	val books: Flow<PagingData<Book>> = settings.librarySort
		.distinctUntilChanged()
		.flatMapLatest { library.books(folderId, it) }
		.cachedIn(viewModelScope)

	val query: StateFlow<String> = savedState.getStateFlow(QUERY, "")

	val results: Flow<PagingData<Book>> = query
		.map { it.trim() }
		.distinctUntilChanged()
		.debounce(SEARCH_DEBOUNCE_MS)
		.flatMapLatest { if (it.isEmpty()) flowOf(PagingData.empty()) else library.search(it) }
		.cachedIn(viewModelScope)

	val importState: StateFlow<ImportState> = importer.state

	val folderTree: StateFlow<List<FolderNode>> = library.folderTree()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

	fun setQuery(value: String) {
		savedState[QUERY] = value
	}

	fun setSort(sort: LibrarySort) {
		viewModelScope.launch { settings.setLibrarySort(sort) }
	}

	fun import(uris: List<Uri>) = importer.import(uris, folderId)

	fun dismissImport() = importer.dismiss()

	suspend fun createFolder(name: String): NameCheck = library.createFolder(folderId, name)

	suspend fun renameFolder(id: Long, name: String): NameCheck = library.renameFolder(id, name)

	suspend fun countsOf(folderId: Long): LibraryCounts = library.counts(folderId).first()

	fun moveFolder(id: Long, parentId: Long?) {
		viewModelScope.launch { library.moveFolder(id, parentId) }
	}

	fun deleteFolder(id: Long) {
		viewModelScope.launch { library.deleteFolder(id) }
	}

	fun moveBook(id: Long, folderId: Long?) {
		viewModelScope.launch { library.moveBook(id, folderId) }
	}

	fun updateBook(id: Long, title: String, author: String) {
		viewModelScope.launch { library.updateBook(id, title, author) }
	}

	fun deleteBook(id: Long) {
		viewModelScope.launch { library.deleteBook(id) }
	}

	suspend fun cover(name: String, minWidth: Int): ImageBitmap? = covers.load(name, minWidth)?.asImageBitmap()

	private companion object {
		const val STOP_MS = 5_000L
		const val SEARCH_DEBOUNCE_MS = 150L
		const val QUERY = "query"
	}
}
