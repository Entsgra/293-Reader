package com.entsgra.reader.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.entsgra.reader.core.data.library.NameCheck
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.DestructiveButton
import com.entsgra.reader.core.designsystem.component.DialogButtons
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderDialog
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ReaderTextField
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.FolderLock
import com.entsgra.reader.core.model.FolderNode
import kotlinx.coroutines.launch

/** Name a new folder or rename one. Stays open with the reason when the name is refused. */
@Composable
internal fun FolderNameDialog(
	title: String,
	initial: String,
	confirmLabel: String,
	onDismiss: () -> Unit,
	onConfirm: suspend (String) -> NameCheck,
) {
	var name by rememberSaveable { mutableStateOf(initial) }
	var check by remember { mutableStateOf(NameCheck.OK) }
	val scope = rememberCoroutineScope()
	val focus = remember { FocusRequester() }
	val submit: () -> Unit = {
		scope.launch {
			check = onConfirm(name)
			if (check == NameCheck.OK) onDismiss()
		}
	}
	ReaderDialog(title = title, onDismiss = onDismiss) {
		ReaderTextField(
			value = name,
			onValueChange = {
				name = it
				check = NameCheck.OK
			},
			label = stringResource(R.string.folder_name),
			error = when (check) {
				NameCheck.OK -> null
				NameCheck.BLANK -> stringResource(R.string.folder_name_blank)
				NameCheck.TOO_LONG -> stringResource(R.string.folder_name_too_long)
				NameCheck.TAKEN -> stringResource(R.string.folder_name_taken)
				NameCheck.LOCKED -> stringResource(R.string.folder_name_locked)
			},
			capitalization = KeyboardCapitalization.Words,
			onImeAction = submit,
			modifier = Modifier.focusRequester(focus),
		)
		DialogButtons {
			PrimaryButton(confirmLabel, onClick = submit, modifier = Modifier.fillMaxWidth())
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
	LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
internal fun BookDetailsDialog(
	book: Book,
	onDismiss: () -> Unit,
	onSave: (title: String, author: String) -> Unit,
) {
	var title by rememberSaveable { mutableStateOf(book.title) }
	var author by rememberSaveable { mutableStateOf(book.author.orEmpty()) }
	var blank by remember { mutableStateOf(false) }
	val save: () -> Unit = {
		if (title.isBlank()) {
			blank = true
		} else {
			onSave(title, author)
			onDismiss()
		}
	}
	ReaderDialog(title = stringResource(R.string.edit_details), onDismiss = onDismiss) {
		ReaderTextField(
			value = title,
			onValueChange = {
				title = it
				blank = false
			},
			label = stringResource(R.string.book_title),
			error = if (blank) stringResource(R.string.book_title_blank) else null,
			imeAction = ImeAction.Next,
		)
		ReaderTextField(
			value = author,
			onValueChange = { author = it },
			label = stringResource(R.string.book_author),
			capitalization = KeyboardCapitalization.Words,
			onImeAction = save,
		)
		DialogButtons {
			PrimaryButton(stringResource(R.string.save), onClick = save, modifier = Modifier.fillMaxWidth())
			SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
		}
	}
}

/**
 * Pick a destination folder. [currentFolderId] is marked and cannot be picked; with
 * [movingFolderId], that folder and everything below it are left out. Closed locked folders
 * are marked and cannot be picked, and neither can anything inside a locked folder when the
 * folder being moved holds a lock of its own (locks do not nest).
 */
@Composable
internal fun MoveDialog(
	tree: List<FolderNode>,
	currentFolderId: Long?,
	onDismiss: () -> Unit,
	onMove: (Long?) -> Unit,
	movingFolderId: Long? = null,
) {
	val choices = remember(tree, movingFolderId) { withoutSubtree(tree, movingFolderId) }
	val blocked = remember(tree, movingFolderId) { blockedDestinations(tree, movingFolderId) }
	val here = stringResource(R.string.move_current)
	val locked = stringResource(R.string.move_locked)
	ReaderDialog(title = stringResource(R.string.move_title), onDismiss = onDismiss) {
		LazyColumn(Modifier.heightIn(max = ReaderSize.rowHeight * MOVE_ROWS_VISIBLE)) {
			item(key = "root") {
				DestinationRow(
					name = stringResource(R.string.library_title),
					depth = 0,
					isCurrent = currentFolderId == null,
					hereLabel = here,
					onClick = {
						onMove(null)
						onDismiss()
					},
				)
			}
			items(choices, key = { it.folder.id }) { node ->
				DestinationRow(
					name = node.folder.name,
					depth = node.depth + 1,
					isCurrent = node.folder.id == currentFolderId,
					hereLabel = here,
					blockedLabel = if (node.folder.id in blocked) locked else null,
					locked = node.folder.lock != FolderLock.NONE,
					onClick = {
						onMove(node.folder.id)
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
private fun DestinationRow(
	name: String,
	depth: Int,
	isCurrent: Boolean,
	hereLabel: String,
	onClick: () -> Unit,
	blockedLabel: String? = null,
	locked: Boolean = false,
) {
	ReaderRow(
		title = name,
		leadingIcon = when {
			depth == 0 -> ReaderIcons.Library
			locked -> ReaderIcons.Lock
			else -> ReaderIcons.Folder
		},
		trailing = if (isCurrent) hereLabel else blockedLabel,
		onClick = if (isCurrent || blockedLabel != null) null else onClick,
		modifier = Modifier.padding(start = ReaderSpacing.space4 * depth.coerceAtMost(MAX_INDENT)),
	)
}

/** Confirms a delete. The destructive button never takes the primary style. */
@Composable
internal fun DeleteDialog(
	title: String,
	body: String,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	ReaderDialog(title = title, onDismiss = onDismiss) {
		BodyText(body)
		DialogButtons {
			DestructiveButton(
				stringResource(R.string.delete),
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

/** Long-press actions: one row per action, delete last and apart. No delete without [onDelete]. */
@Composable
internal fun ActionsDialog(
	title: String,
	actions: List<Pair<String, () -> Unit>>,
	onDelete: (() -> Unit)?,
	onDismiss: () -> Unit,
) {
	ReaderDialog(title = title, onDismiss = onDismiss) {
		actions.forEach { (label, action) ->
			ReaderRow(title = label.uppercase(), onClick = action)
		}
		if (onDelete != null) {
			DialogButtons {
				DestructiveButton(stringResource(R.string.delete), onClick = onDelete, modifier = Modifier.fillMaxWidth())
			}
		}
	}
}

/**
 * Destinations that cannot be picked: closed locked folders, and, when [movingFolderId]'s
 * subtree holds a lock, every folder at or inside a locked one.
 */
internal fun blockedDestinations(tree: List<FolderNode>, movingFolderId: Long?): Set<Long> {
	val closed = tree.filter { it.closed }.map { it.folder.id }.toSet()
	val start = tree.indexOfFirst { it.folder.id == movingFolderId }
	if (start < 0) return closed
	val depth = tree[start].depth
	var end = start + 1
	while (end < tree.size && tree[end].depth > depth) end++
	val holdsLock = tree.subList(start, end).any { it.folder.lock != FolderLock.NONE }
	return if (holdsLock) closed + insideLocks(tree) else closed
}

/** Folders that are locked or below a locked one; the tree is depth first. */
internal fun insideLocks(tree: List<FolderNode>): Set<Long> {
	val out = HashSet<Long>()
	var lockedDepth = -1
	for (node in tree) {
		if (lockedDepth >= 0 && node.depth <= lockedDepth) lockedDepth = -1
		if (lockedDepth < 0 && node.folder.lock != FolderLock.NONE) lockedDepth = node.depth
		if (lockedDepth >= 0) out += node.folder.id
	}
	return out
}

/** [tree] without [folderId] and its descendants: the flattened tree keeps them contiguous. */
internal fun withoutSubtree(tree: List<FolderNode>, folderId: Long?): List<FolderNode> {
	if (folderId == null) return tree
	val start = tree.indexOfFirst { it.folder.id == folderId }
	if (start < 0) return tree
	val depth = tree[start].depth
	var end = start + 1
	while (end < tree.size && tree[end].depth > depth) end++
	return tree.subList(0, start) + tree.subList(end, tree.size)
}

private const val MOVE_ROWS_VISIBLE = 6
/** Deep trees stop indenting so names keep room. */
private const val MAX_INDENT = 4
