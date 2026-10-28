package com.entsgra.reader.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.DestructiveButton
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.component.WindowPlate
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Folder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class BookDialog { EDIT, MOVE, DELETE }

@Composable
internal fun BookScreen(
	onBack: () -> Unit,
	viewModel: BookViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val tree by viewModel.folderTree.collectAsStateWithLifecycle()
	var dialog by remember { mutableStateOf<BookDialog?>(null) }

	LaunchedEffect(state) { if (state == BookUiState.Gone) onBack() }
	val ready = state as? BookUiState.Ready ?: return
	val book = ready.book

	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4)
			.padding(bottom = ReaderSpacing.space8),
	) {
		ScreenTitle(book.title, onBack = onBack, backLabel = stringResource(R.string.back))
		SweepKeyCard {
			WindowPlate(
				label = stringResource(R.string.book_pages),
				figure = book.pageCount?.toString() ?: "–",
				plateLabel = stringResource(R.string.book_format),
				plateValue = book.format.name,
			)
			book.author?.let { BodyText(it) }
			BodyText(stringResource(R.string.book_read_later))
		}
		PrimaryButton(
			text = stringResource(R.string.book_read),
			onClick = {},
			enabled = false,
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = ReaderSpacing.space3),
		)
		BookFacts(book, ready.folder, Modifier.padding(top = ReaderSpacing.space6))
		Column(
			Modifier.padding(top = ReaderSpacing.space6),
			verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
		) {
			SecondaryButton(
				stringResource(R.string.edit_details),
				onClick = { dialog = BookDialog.EDIT },
				modifier = Modifier.fillMaxWidth(),
			)
			SecondaryButton(
				stringResource(R.string.move),
				onClick = { dialog = BookDialog.MOVE },
				modifier = Modifier.fillMaxWidth(),
			)
			DestructiveButton(
				stringResource(R.string.delete),
				onClick = { dialog = BookDialog.DELETE },
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}

	val dismiss = { dialog = null }
	when (dialog) {
		null -> Unit
		BookDialog.EDIT -> BookDetailsDialog(book, onDismiss = dismiss, onSave = viewModel::update)
		BookDialog.MOVE -> MoveDialog(tree, currentFolderId = book.folderId, onDismiss = dismiss, onMove = viewModel::move)
		BookDialog.DELETE -> DeleteDialog(
			title = stringResource(R.string.delete_book_title),
			body = stringResource(R.string.delete_book_body),
			onDismiss = dismiss,
			onConfirm = viewModel::delete,
		)
	}
}

@Composable
private fun BookFacts(book: Book, folder: Folder?, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	Column(modifier) {
		ReaderRow(
			title = stringResource(R.string.book_folder),
			trailing = folder?.name ?: stringResource(R.string.library_title),
		)
		ReaderRow(title = stringResource(R.string.book_added), trailing = shortDate(book.addedAt))
		ReaderRow(
			title = stringResource(R.string.book_size),
			trailing = Formatter.formatShortFileSize(context, book.fileSize),
		)
	}
}

/** "OCT 27", or "OCT 27, 2025" outside the current year. */
private fun shortDate(epochMillis: Long): String {
	val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
	val pattern = if (date.year == LocalDate.now().year) "MMM d" else "MMM d, yyyy"
	return date.format(DateTimeFormatter.ofPattern(pattern))
}
