package com.entsgra.reader.feature.physical

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.FieldLabel
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SectionHeadline
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.component.WindowPlate
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
internal fun ShelfScreen(
	onOpenBook: (Long) -> Unit,
	viewModel: ShelfViewModel = hiltViewModel(),
) {
	val content by viewModel.books.collectAsStateWithLifecycle()
	var adding by rememberSaveable { mutableStateOf(false) }
	val scope = rememberCoroutineScope()

	content?.let { ShelfContentView(it, onAdd = { adding = true }, onOpenBook = onOpenBook) }

	if (adding) {
		PhysicalBookDialog(
			title = stringResource(R.string.physical_add),
			book = null,
			onDismiss = { adding = false },
			onSave = { details, refused ->
				scope.launch {
					val (id, check) = viewModel.add(details)
					if (id != null) {
						adding = false
						onOpenBook(id)
					} else {
						refused(check)
					}
				}
			},
		)
	}
}

/** The shelf: the book being read on the key card, the add button, then the rest by status. */
@Composable
internal fun ShelfContentView(content: ShelfContent, onAdd: () -> Unit, onOpenBook: (Long) -> Unit) {
	LazyColumn(
		Modifier
			.fillMaxSize()
			.padding(horizontal = ReaderSpacing.space4),
	) {
		item { ScreenTitle(stringResource(R.string.physical_title)) }
		content.current?.let { book ->
			item(key = "current") {
				SweepKeyCard(onClick = { onOpenBook(book.id) }) {
					FieldLabel(stringResource(R.string.physical_now_reading))
					BodyText(book.title)
					PagePlate(book)
				}
			}
		}
		if (content.isEmpty) item { BodyText(stringResource(R.string.physical_empty)) }
		item {
			PrimaryButton(
				text = stringResource(R.string.physical_add),
				onClick = onAdd,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = ReaderSpacing.space3),
			)
		}
		content.groups.forEach { (status, books) ->
			item(key = status.name) {
				SectionHeadline(statusLabel(status), Modifier.padding(top = ReaderSpacing.space8, bottom = ReaderSpacing.space2))
			}
			items(books, key = { it.id }) { book ->
				ReaderRow(
					title = book.title,
					trailing = progressPercent(book)?.let { stringResource(R.string.physical_percent, it) },
					onClick = { onOpenBook(book.id) },
				)
			}
		}
		item { Spacer(Modifier.padding(bottom = ReaderSpacing.space8)) }
	}
}

/** The page the bookmark is at, over the book's length when known. */
@Composable
internal fun PagePlate(book: PhysicalBook) {
	WindowPlate(
		label = stringResource(R.string.physical_page),
		figure = if (book.currentPage > 0) book.currentPage.toString() else "–",
		plateLabel = stringResource(R.string.physical_of),
		plateValue = book.pageCount?.toString() ?: "–",
	)
}

/** Percent through a book being read, when its length is known; null otherwise. */
internal fun progressPercent(book: PhysicalBook): Int? {
	val pages = book.pageCount ?: return null
	if (book.status != ReadingStatus.READING || book.currentPage <= 0) return null
	return (book.currentPage * 100f / pages).roundToInt().coerceIn(0, 100)
}
