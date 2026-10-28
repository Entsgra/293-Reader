package com.entsgra.reader.feature.physical

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.entsgra.reader.core.data.physical.PhysicalBookDetails
import com.entsgra.reader.core.data.physical.ShelfCheck
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.DestructiveButton
import com.entsgra.reader.core.designsystem.component.DialogButtons
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderDialog
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ReaderTextField
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingStatus

/**
 * Title, author and page count, for a new book ([book] null, which starts as To read) or an
 * existing one. [onSave] reports a refusal back through its second argument.
 */
@Composable
internal fun PhysicalBookDialog(
	title: String,
	book: PhysicalBook?,
	onDismiss: () -> Unit,
	onSave: (PhysicalBookDetails, refused: (ShelfCheck) -> Unit) -> Unit,
) {
	var name by rememberSaveable { mutableStateOf(book?.title.orEmpty()) }
	var author by rememberSaveable { mutableStateOf(book?.author.orEmpty()) }
	var pages by rememberSaveable { mutableStateOf(book?.pageCount?.toString().orEmpty()) }
	var problem by remember { mutableStateOf(ShelfCheck.OK) }
	val save: () -> Unit = {
		onSave(PhysicalBookDetails(name, author, pages, book?.status ?: ReadingStatus.WANT_TO_READ)) { problem = it }
	}
	ReaderDialog(title = title, onDismiss = onDismiss) {
		ReaderTextField(
			value = name,
			onValueChange = {
				name = it
				problem = ShelfCheck.OK
			},
			label = stringResource(R.string.physical_title_field),
			error = if (problem == ShelfCheck.BLANK_TITLE) stringResource(R.string.physical_title_blank) else null,
			imeAction = ImeAction.Next,
		)
		ReaderTextField(
			value = author,
			onValueChange = { author = it },
			label = stringResource(R.string.physical_author_field),
			capitalization = KeyboardCapitalization.Words,
			imeAction = ImeAction.Next,
		)
		ReaderTextField(
			value = pages,
			onValueChange = {
				pages = it
				problem = ShelfCheck.OK
			},
			label = stringResource(R.string.physical_pages_field),
			error = if (problem == ShelfCheck.BAD_PAGE_COUNT) stringResource(R.string.physical_pages_bad) else null,
			keyboardType = KeyboardType.Number,
			onImeAction = save,
		)
		DialogButtons {
			PrimaryButton(stringResource(R.string.save), onClick = save, modifier = Modifier.fillMaxWidth())
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
}

/** A sitting that just ended: pages from and to, and how long. From starts after the bookmark. */
@Composable
internal fun LogSittingDialog(
	book: PhysicalBook,
	onDismiss: () -> Unit,
	onSave: (from: String, to: String, minutes: String, refused: (ShelfCheck) -> Unit) -> Unit,
) {
	val start = (book.currentPage + 1).let { next -> book.pageCount?.let { next.coerceAtMost(it) } ?: next }
	var from by rememberSaveable { mutableStateOf(start.toString()) }
	var to by rememberSaveable { mutableStateOf("") }
	var minutes by rememberSaveable { mutableStateOf("") }
	var problem by remember { mutableStateOf(ShelfCheck.OK) }
	val save: () -> Unit = { onSave(from, to, minutes) { problem = it } }
	val pagesError = if (problem == ShelfCheck.BAD_PAGES) stringResource(R.string.physical_pages_range) else null
	ReaderDialog(title = stringResource(R.string.physical_log), onDismiss = onDismiss) {
		ReaderTextField(
			value = from,
			onValueChange = {
				from = it
				problem = ShelfCheck.OK
			},
			label = stringResource(R.string.physical_from_field),
			keyboardType = KeyboardType.Number,
			imeAction = ImeAction.Next,
		)
		ReaderTextField(
			value = to,
			onValueChange = {
				to = it
				problem = ShelfCheck.OK
			},
			label = stringResource(R.string.physical_to_field),
			error = pagesError,
			keyboardType = KeyboardType.Number,
			imeAction = ImeAction.Next,
		)
		ReaderTextField(
			value = minutes,
			onValueChange = {
				minutes = it
				problem = ShelfCheck.OK
			},
			label = stringResource(R.string.physical_minutes_field),
			error = if (problem == ShelfCheck.BAD_MINUTES) stringResource(R.string.physical_minutes_bad) else null,
			keyboardType = KeyboardType.Number,
			onImeAction = save,
		)
		DialogButtons {
			PrimaryButton(stringResource(R.string.save), onClick = save, modifier = Modifier.fillMaxWidth())
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
}

/** Four statuses: a list, ticked at the current one. */
@Composable
internal fun StatusDialog(current: ReadingStatus, onDismiss: () -> Unit, onPick: (ReadingStatus) -> Unit) {
	ReaderDialog(title = stringResource(R.string.physical_status), onDismiss = onDismiss) {
		Column {
			STATUS_ORDER.forEach { status ->
				ReaderRow(
					title = statusLabel(status),
					leadingIcon = if (status == current) ReaderIcons.Check else null,
					onClick = {
						onPick(status)
						onDismiss()
					},
				)
			}
		}
		DialogButtons {
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Composable
internal fun ConfirmDeleteDialog(
	title: String,
	body: String,
	action: String,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	ReaderDialog(title = title, onDismiss = onDismiss) {
		BodyText(body)
		DialogButtons {
			DestructiveButton(
				action,
				onClick = {
					onConfirm()
					onDismiss()
				},
				modifier = Modifier.fillMaxWidth(),
			)
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Composable
internal fun statusLabel(status: ReadingStatus): String = stringResource(
	when (status) {
		ReadingStatus.WANT_TO_READ -> R.string.physical_status_want
		ReadingStatus.READING -> R.string.physical_status_reading
		ReadingStatus.FINISHED -> R.string.physical_status_finished
		ReadingStatus.ABANDONED -> R.string.physical_status_abandoned
	},
)
