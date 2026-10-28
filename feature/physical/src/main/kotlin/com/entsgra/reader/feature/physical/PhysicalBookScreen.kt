package com.entsgra.reader.feature.physical

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.data.physical.ShelfCheck
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.DestructiveButton
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.component.SectionHeadline
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private sealed interface BookDialog {
	data object Edit : BookDialog
	data object Log : BookDialog
	data object Status : BookDialog
	data object Delete : BookDialog
	data class DeleteSitting(val id: Long) : BookDialog
}

@Composable
internal fun PhysicalBookScreen(
	onBack: () -> Unit,
	viewModel: PhysicalBookViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	var dialog by remember { mutableStateOf<BookDialog?>(null) }
	val scope = rememberCoroutineScope()

	LaunchedEffect(state) { if (state == PhysicalBookUiState.Gone) onBack() }
	val ready = state as? PhysicalBookUiState.Ready ?: return
	val book = ready.book

	PhysicalBookContent(
		book = book,
		sittings = ready.sittings,
		onBack = onBack,
		onLog = { dialog = BookDialog.Log },
		onStatus = { dialog = BookDialog.Status },
		onDeleteSitting = { dialog = BookDialog.DeleteSitting(it) },
		onEdit = { dialog = BookDialog.Edit },
		onDelete = { dialog = BookDialog.Delete },
	)

	val dismiss = { dialog = null }
	when (val open = dialog) {
		null -> Unit
		BookDialog.Edit -> PhysicalBookDialog(
			title = stringResource(R.string.physical_edit),
			book = book,
			onDismiss = dismiss,
			onSave = { details, refused ->
				scope.launch {
					val check = viewModel.update(details)
					if (check == ShelfCheck.OK) dismiss() else refused(check)
				}
			},
		)
		BookDialog.Log -> LogSittingDialog(
			book = book,
			onDismiss = dismiss,
			onSave = { from, to, minutes, refused ->
				scope.launch {
					val check = viewModel.logSitting(from, to, minutes)
					if (check == ShelfCheck.OK) dismiss() else refused(check)
				}
			},
		)
		BookDialog.Status -> StatusDialog(book.status, onDismiss = dismiss, onPick = viewModel::setStatus)
		BookDialog.Delete -> ConfirmDeleteDialog(
			title = stringResource(R.string.physical_delete_title),
			body = stringResource(R.string.physical_delete_body),
			action = stringResource(R.string.physical_delete),
			onDismiss = dismiss,
			onConfirm = viewModel::delete,
		)
		is BookDialog.DeleteSitting -> ConfirmDeleteDialog(
			title = stringResource(R.string.physical_delete_sitting_title),
			body = stringResource(R.string.physical_delete_sitting_body),
			action = stringResource(R.string.physical_delete_sitting_action),
			onDismiss = dismiss,
			onConfirm = { viewModel.deleteSitting(open.id) },
		)
	}
}

/**
 * One physical book: where the bookmark is, logging a sitting (the one primary action), its
 * status, and the sittings logged so far. Long press a sitting to delete it.
 */
@Composable
internal fun PhysicalBookContent(
	book: PhysicalBook,
	sittings: List<ReadingSession>,
	onBack: () -> Unit,
	onLog: () -> Unit,
	onStatus: () -> Unit,
	onDeleteSitting: (Long) -> Unit,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
) {
	val deleteLabel = stringResource(R.string.physical_delete_sitting_action)
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = ReaderSpacing.space4)
			.padding(bottom = ReaderSpacing.space8),
	) {
		ScreenTitle(book.title, onBack = onBack, backLabel = stringResource(R.string.back))
		SweepKeyCard {
			PagePlate(book)
			book.author?.let { BodyText(it) }
		}
		PrimaryButton(
			text = stringResource(R.string.physical_log),
			onClick = onLog,
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = ReaderSpacing.space3),
		)
		Column(Modifier.padding(top = ReaderSpacing.space6)) {
			ReaderRow(
				title = stringResource(R.string.physical_status),
				trailing = statusLabel(book.status),
				onClick = onStatus,
			)
			ReaderRow(title = stringResource(R.string.physical_added), trailing = shortDate(book.addedAt))
		}
		SectionHeadline(
			stringResource(R.string.physical_sittings),
			Modifier.padding(top = ReaderSpacing.space8, bottom = ReaderSpacing.space2),
		)
		if (sittings.isEmpty()) BodyText(stringResource(R.string.physical_no_sittings))
		sittings.forEach { sitting ->
			val end = sitting.endPage ?: sitting.startPage
			ReaderRow(
				title = if (end == sitting.startPage) {
					stringResource(R.string.physical_sitting_page, end)
				} else {
					stringResource(R.string.physical_sitting_pages, sitting.startPage, end)
				},
				trailing = shortDate(sitting.startedAt),
				onLongClick = { onDeleteSitting(sitting.id) },
				onLongClickLabel = deleteLabel,
			)
		}
		Column(
			Modifier.padding(top = ReaderSpacing.space6),
			verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
		) {
			SecondaryButton(stringResource(R.string.physical_edit), onClick = onEdit, modifier = Modifier.fillMaxWidth())
			DestructiveButton(stringResource(R.string.physical_delete), onClick = onDelete, modifier = Modifier.fillMaxWidth())
		}
	}
}

/** "OCT 27", or "OCT 27, 2025" outside the current year. */
internal fun shortDate(epochMillis: Long): String {
	val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
	val pattern = if (date.year == LocalDate.now().year) "MMM d" else "MMM d, yyyy"
	return date.format(DateTimeFormatter.ofPattern(pattern))
}
