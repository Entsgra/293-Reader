package com.entsgra.reader.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.data.reader.ReaderRepository
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.ReadingTextSize
import com.entsgra.reader.core.model.SpreadMode
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.FlowDocument
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
	data object Loading : ReaderUiState

	/** The book was deleted while the reader was opening. */
	data object Gone : ReaderUiState

	data class Failed(val reason: BookOpenException.Reason) : ReaderUiState

	data class Paged(val document: PagedDocument) : ReaderUiState

	data class Flow(val document: FlowDocument) : ReaderUiState
}

/**
 * Holds the open document across rotation, so turning the phone does not reopen (and for a PDF,
 * decrypt again) the book. Saves the position shortly after it settles and when the screen
 * stops, and logs each sitting from start to stop.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
	savedState: SavedStateHandle,
	private val repository: ReaderRepository,
	private val settings: SettingsRepository,
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

	/** Where a flow book (EPUB, TXT) is open: the start of the page on screen. */
	private val _text = MutableStateFlow(Locator.Text(0, 0))
	val text: StateFlow<Locator.Text> = _text.asStateFlow()
	private var textProgress = 0f

	val textSize: StateFlow<ReadingTextSize> = settings.readingTextSize
		.stateIn(viewModelScope, SharingStarted.Eagerly, ReadingTextSize.MEDIUM)

	init {
		viewModelScope.launch { open() }
		viewModelScope.launch {
			merge(_page.drop(1), _text.drop(1)).debounce(SAVE_DELAY_MS).collect { savePosition() }
		}
	}

	/** [pages] is what is on screen: one page, or a spread. */
	fun onPageChange(pages: IntRange) {
		lastVisible = pages.last
		_page.value = pages.first
	}

	fun setReadingDirection(direction: ReadingDirection) = repository.setReadingDirection(bookId, direction)

	fun setSpreadMode(mode: SpreadMode) = repository.setSpreadMode(bookId, mode)

	/** A flow book settled on the page starting at [locator], [progress] through the book. */
	fun onTextPosition(locator: Locator.Text, progress: Float) {
		textProgress = progress
		_text.value = locator
	}

	fun setTextSize(size: ReadingTextSize) {
		viewModelScope.launch { settings.setReadingTextSize(size) }
	}

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
		val saved = repository.lastPosition(bookId)
		when (opened) {
			is PagedDocument -> {
				if (opened.pageCount == 0) {
					_state.value = ReaderUiState.Failed(BookOpenException.Reason.EMPTY)
					return
				}
				_page.value = ((saved?.locator as? Locator.Page)?.index ?: 0).coerceIn(0, opened.pageCount - 1)
				lastVisible = _page.value
				_state.value = ReaderUiState.Paged(opened)
			}
			is FlowDocument -> {
				if (opened.chapterCount == 0) {
					_state.value = ReaderUiState.Failed(BookOpenException.Reason.EMPTY)
					return
				}
				val text = saved?.locator as? Locator.Text
				_text.value = if (text != null && text.chapter < opened.chapterCount) {
					Locator.Text(text.chapter.coerceAtLeast(0), text.offset.coerceAtLeast(0))
				} else {
					Locator.Text(0, 0)
				}
				textProgress = if (text != null) saved.progress else 0f
				_state.value = ReaderUiState.Flow(opened)
			}
		}
		beginSessionIfReady()
	}

	private fun beginSessionIfReady() {
		if (!started || sessionOpen) return
		val (page, progress) = sessionPoint() ?: return
		sessionOpen = true
		repository.beginSession(bookId, page, progress)
	}

	private fun endSession() {
		if (!sessionOpen) return
		sessionOpen = false
		val (page, progress) = sessionPoint() ?: return
		repository.finishSession(page, progress)
	}

	/** The 1-based page (0 for flow books, which have none) and progress, for the reading log. */
	private fun sessionPoint(): Pair<Int, Float>? = when (val current = _state.value) {
		is ReaderUiState.Paged -> _page.value + 1 to pagedProgress(current.document)
		is ReaderUiState.Flow -> 0 to textProgress
		else -> null
	}

	private fun savePosition() {
		when (val current = _state.value) {
			is ReaderUiState.Paged ->
				repository.savePosition(bookId, Locator.Page(_page.value), pagedProgress(current.document))
			is ReaderUiState.Flow -> repository.savePosition(bookId, _text.value, textProgress)
			else -> Unit
		}
	}

	/** Counts the last page on screen, so the final spread reads 100%. */
	private fun pagedProgress(document: PagedDocument): Float =
		(maxOf(lastVisible, _page.value) + 1) / document.pageCount.toFloat()

	private companion object {
		const val SAVE_DELAY_MS = 500L
	}
}
