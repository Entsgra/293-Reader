package com.entsgra.reader.feature.physical

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entsgra.reader.core.data.physical.PhysicalBookDetails
import com.entsgra.reader.core.data.physical.ShelfCheck
import com.entsgra.reader.core.data.physical.ShelfRepository
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingSession
import com.entsgra.reader.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PhysicalBookUiState {
	data object Loading : PhysicalBookUiState

	/** Deleted, here or elsewhere; the screen closes. */
	data object Gone : PhysicalBookUiState

	data class Ready(val book: PhysicalBook, val sittings: List<ReadingSession>) : PhysicalBookUiState
}

@HiltViewModel
class PhysicalBookViewModel @Inject constructor(
	savedState: SavedStateHandle,
	private val shelf: ShelfRepository,
) : ViewModel() {
	private val bookId = savedState.toRoute<PhysicalBookRoute>().bookId

	val state: StateFlow<PhysicalBookUiState> = combine(shelf.book(bookId), shelf.sessions(bookId)) { book, sittings ->
		if (book == null) PhysicalBookUiState.Gone else PhysicalBookUiState.Ready(book, sittings)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), PhysicalBookUiState.Loading)

	suspend fun update(details: PhysicalBookDetails): ShelfCheck = shelf.update(bookId, details)

	suspend fun logSitting(from: String, to: String, minutes: String): ShelfCheck =
		shelf.logSitting(bookId, from, to, minutes)

	fun setStatus(status: ReadingStatus) {
		viewModelScope.launch { shelf.setStatus(bookId, status) }
	}

	fun deleteSitting(id: Long) {
		viewModelScope.launch { shelf.deleteSitting(id) }
	}

	fun delete() {
		viewModelScope.launch { shelf.delete(bookId) }
	}

	private companion object {
		const val STOP_MS = 5_000L
	}
}
