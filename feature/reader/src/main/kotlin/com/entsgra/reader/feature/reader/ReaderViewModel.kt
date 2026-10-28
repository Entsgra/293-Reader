package com.entsgra.reader.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entsgra.reader.core.data.reader.ReaderRepository
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.SpreadMode
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.PagedDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
	data object Loading : ReaderUiState

	/** The book was deleted while the reader was opening. */
	data object Gone : ReaderUiState

	data class Failed(val reason: BookOpenException.Reason) : ReaderUiState

	data class Paged(val document: PagedDocument) : ReaderUiState
}

/**
 * Holds the open document across rotation, so turning the phone does not reopen (and for a PDF,
 * decrypt again) the book. Saves the page shortly after it settles and when the screen stops,
 * and logs each sitting from start to stop.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
	savedState: SavedStateHandle,
	private val repository: ReaderRepository,
	val bitmapPool: BitmapPool,
) : ViewModel() {
	private val bookId = savedState.toRoute<ReaderRoute>().bookId
	private var document: BookDocument? = null
	private var started = false
	private var sessionOpen = false
	private var lastVisible = 0

	private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
	val state: StateFlow<ReaderUiState> = _state.asStateFlow()

	val book: StateFlow<Book?> = repository.book(bookId)
		.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	/** Index of the first page on screen. */
	private val _page = MutableStateFlow(0)
	val page: StateFlow<Int> = _page.asStateFlow()

	init {
		viewModelScope.launch { open() }
		viewModelScope.launch {
			_page.drop(1).debounce(SAVE_DELAY_MS).collect { savePosition() }
		}
	}

	/** [pages] is what is on screen: one page, or a spread. */
	fun onPageChange(pages: IntRange) {
		lastVisible = pages.last
		_page.value = pages.first
	}

	fun setReadingDirection(direction: ReadingDirection) = repository.setReadingDirection(bookId, direction)

	fun setSpreadMode(mode: SpreadMode) = repository.setSpreadMode(bookId, mode)

	/** The screen is visible: start logging, once the book is open. */
	fun onStart() {
		started = true
		beginSessionIfReady()
	}

	/** The screen is hidden: close the log entry and save the page now, not after the delay. */
	fun onStop() {
		started = false
		endSession()
		savePosition()
	}

	override fun onCleared() {
		endSession()
		savePosition()
		document?.let(repository::close)
		document = null
	}

	private suspend fun open() {
		val book = repository.book(bookId).first() ?: run {
			_state.value = ReaderUiState.Gone
			return
		}
		val opened = try {
			repository.open(book)
		} catch (e: BookOpenException) {
			_state.value = ReaderUiState.Failed(e.reason)
			return
		}
		document = opened
		if (opened !is PagedDocument) {
			_state.value = ReaderUiState.Failed(BookOpenException.Reason.UNSUPPORTED)
			return
		}
		if (opened.pageCount == 0) {
			_state.value = ReaderUiState.Failed(BookOpenException.Reason.EMPTY)
			return
		}
		val saved = repository.lastPosition(bookId)?.locator as? Locator.Page
		_page.value = (saved?.index ?: 0).coerceIn(0, opened.pageCount - 1)
		lastVisible = _page.value
		_state.value = ReaderUiState.Paged(opened)
		beginSessionIfReady()
	}

	private fun beginSessionIfReady() {
		if (!started || sessionOpen || _state.value !is ReaderUiState.Paged) return
		sessionOpen = true
		repository.beginSession(bookId, _page.value + 1)
	}

	private fun endSession() {
		if (!sessionOpen) return
		sessionOpen = false
		repository.finishSession(_page.value + 1)
	}

	private fun savePosition() {
		val paged = _state.value as? ReaderUiState.Paged ?: return
		// Progress counts the last page on screen, so the final spread reads 100%.
		val progress = (maxOf(lastVisible, _page.value) + 1) / paged.document.pageCount.toFloat()
		repository.savePosition(bookId, Locator.Page(_page.value), progress)
	}

	private companion object {
		const val SAVE_DELAY_MS = 500L
	}
}
