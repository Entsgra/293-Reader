package com.entsgra.reader.feature.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entsgra.reader.core.data.physical.PhysicalBookDetails
import com.entsgra.reader.core.data.physical.ShelfCheck
import com.entsgra.reader.core.data.physical.ShelfRepository
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The shelf: null while the database opens. */
@HiltViewModel
class ShelfViewModel @Inject constructor(
	private val shelf: ShelfRepository,
) : ViewModel() {
	val books: StateFlow<ShelfContent?> = shelf.books()
		.map(::shelfContent)
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

	/** Adds a book and returns its id, or why the details were refused. */
	suspend fun add(details: PhysicalBookDetails): Pair<Long?, ShelfCheck> =
		when (val result = shelf.add(details)) {
			is ShelfRepository.Result.Saved -> result.id to ShelfCheck.OK
			is ShelfRepository.Result.Refused -> null to result.reason
		}

	private companion object {
		const val STOP_MS = 5_000L
	}
}

/** The shelf as shown: the book being read on the key card, the rest grouped by status. */
data class ShelfContent(val current: PhysicalBook?, val groups: List<Pair<ReadingStatus, List<PhysicalBook>>>) {
	val isEmpty get() = current == null && groups.isEmpty()
}

/**
 * Splits [books] (most recently touched first) for the shelf: the latest book being read goes
 * on the key card, and is said once, so it is left out of the Reading group below.
 */
internal fun shelfContent(books: List<PhysicalBook>): ShelfContent {
	val current = books.firstOrNull { it.status == ReadingStatus.READING }
	val rest = books.filter { it.id != current?.id }
	val groups = STATUS_ORDER.mapNotNull { status ->
		rest.filter { it.status == status }.takeIf { it.isNotEmpty() }?.let { status to it }
	}
	return ShelfContent(current, groups)
}

internal val STATUS_ORDER = listOf(
	ReadingStatus.READING,
	ReadingStatus.WANT_TO_READ,
	ReadingStatus.FINISHED,
	ReadingStatus.ABANDONED,
)
