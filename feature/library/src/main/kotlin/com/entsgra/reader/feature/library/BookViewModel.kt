package com.entsgra.reader.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entsgra.reader.core.data.library.LibraryRepository
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderNode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the book screen shows. [gone] once the book is deleted, so the screen can close. */
sealed interface BookUiState {
	data object Loading : BookUiState
	data object Gone : BookUiState
	data class Ready(val book: Book, val folder: Folder?) : BookUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookViewModel @Inject constructor(
	savedState: SavedStateHandle,
	private val library: LibraryRepository,
) : ViewModel() {
	private val bookId = savedState.toRoute<BookRoute>().bookId

	val state: StateFlow<BookUiState> = library.book(bookId)
		.flatMapLatest { book ->
			val folderId = book?.folderId
			when {
				book == null -> flowOf(BookUiState.Gone)
				folderId == null -> flowOf(BookUiState.Ready(book, null))
				else -> library.folder(folderId).map { BookUiState.Ready(book, it) }
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), BookUiState.Loading)

	val folderTree: StateFlow<List<FolderNode>> = library.folderTree()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

	fun update(title: String, author: String) {
		viewModelScope.launch { library.updateBook(bookId, title, author) }
	}

	fun move(folderId: Long?) {
		viewModelScope.launch { library.moveBook(bookId, folderId) }
	}

	fun delete() {
		viewModelScope.launch { library.deleteBook(bookId) }
	}

	private companion object {
		const val STOP_MS = 5_000L
	}
}
