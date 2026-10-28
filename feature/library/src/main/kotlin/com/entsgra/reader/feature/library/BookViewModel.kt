package com.entsgra.reader.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entsgra.reader.core.data.library.LibraryRepository
import com.entsgra.reader.core.data.reader.ReaderRepository
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderNode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the book screen shows. [gone] once the book is deleted, so the screen can close. */
sealed interface BookUiState {
	data object Loading : BookUiState
	data object Gone : BookUiState
	/** [readable] when a reader exists for the format; [progress] is null until first opened. */
	data class Ready(
		val book: Book,
		val folder: Folder?,
		val readable: Boolean,
		val progress: Float?,
	) : BookUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookViewModel @Inject constructor(
	savedState: SavedStateHandle,
	private val library: LibraryRepository,
	private val reader: ReaderRepository,
) : ViewModel() {
	private val bookId = savedState.toRoute<BookRoute>().bookId

	val state: StateFlow<BookUiState> = library.book(bookId)
		.flatMapLatest { book ->
			val folderId = book?.folderId
			when {
				book == null -> flowOf(BookUiState.Gone)
				else -> combine(
					if (folderId == null) flowOf(null) else library.folder(folderId),
					reader.position(bookId),
				) { folder, position ->
					BookUiState.Ready(book, folder, reader.canRead(book), position?.progress)
				}
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
