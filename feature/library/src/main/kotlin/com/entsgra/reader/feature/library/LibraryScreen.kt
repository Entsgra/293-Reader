package com.entsgra.reader.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.entsgra.reader.core.data.library.ImportFailure
import com.entsgra.reader.core.data.library.ImportState
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.CoverTile
import com.entsgra.reader.core.designsystem.component.CoverTilePlaceholder
import com.entsgra.reader.core.designsystem.component.ErrorNotice
import com.entsgra.reader.core.designsystem.component.LiveStatus
import com.entsgra.reader.core.designsystem.component.PrimaryButton
import com.entsgra.reader.core.designsystem.component.ReaderChips
import com.entsgra.reader.core.designsystem.component.ReaderIcon
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ReaderTextField
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.component.SectionHeadline
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.component.WindowPlate
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Folder
import com.entsgra.reader.core.model.FolderItem
import com.entsgra.reader.core.model.LibrarySort
import kotlinx.coroutines.launch

/** What a long press or a button opened. One dialog at a time. */
private sealed interface LibraryDialog {
	data object NewFolder : LibraryDialog
	data class FolderActions(val folder: Folder) : LibraryDialog
	data class RenameFolder(val folder: Folder) : LibraryDialog
	data class MoveFolder(val folder: Folder) : LibraryDialog
	data class DeleteFolder(val folder: Folder, val books: Int, val folders: Int) : LibraryDialog
	data class BookActions(val book: Book) : LibraryDialog
	data class EditBook(val book: Book) : LibraryDialog
	data class MoveBook(val book: Book) : LibraryDialog
	data class DeleteBook(val book: Book) : LibraryDialog
}

@Composable
internal fun LibraryScreen(
	onOpenFolder: (Long) -> Unit,
	onOpenBook: (Long) -> Unit,
	onBack: () -> Unit,
	viewModel: LibraryViewModel = hiltViewModel(),
) {
	val folder by viewModel.folder.collectAsStateWithLifecycle()
	val folderGone by viewModel.folderGone.collectAsStateWithLifecycle()
	val folders by viewModel.folders.collectAsStateWithLifecycle()
	val counts by viewModel.counts.collectAsStateWithLifecycle()
	val sort by viewModel.sort.collectAsStateWithLifecycle()
	val query by viewModel.query.collectAsStateWithLifecycle()
	val importState by viewModel.importState.collectAsStateWithLifecycle()
	val books = viewModel.books.collectAsLazyPagingItems()
	val results = viewModel.results.collectAsLazyPagingItems()
	val isRoot = viewModel.folderId == null
	val searching = isRoot && query.isNotBlank()

	var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
	val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
		viewModel.import(uris)
	}

	LaunchedEffect(folderGone) { if (folderGone) onBack() }

	val coverPx = with(LocalDensity.current) { ReaderSize.coverMinWidth.roundToPx() * COVER_SCALE }
	val tile: @Composable (Book) -> Unit = { book ->
		val image by produceState<ImageBitmap?>(null, book.coverFileName) {
			value = book.coverFileName?.let { viewModel.cover(it, coverPx) }
		}
		CoverTile(
			title = book.title,
			formatLabel = book.format.name,
			image = image,
			onClick = { onOpenBook(book.id) },
			onLongClick = { dialog = LibraryDialog.BookActions(book) },
			onLongClickLabel = stringResource(R.string.show_actions),
		)
	}

	LazyVerticalGrid(
		columns = GridCells.Adaptive(ReaderSize.coverMinWidth),
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(
			start = ReaderSpacing.space4,
			end = ReaderSpacing.space4,
			bottom = ReaderSpacing.space8,
		),
		horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
		verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
	) {
		fullWidth("title") {
			ScreenTitle(
				text = folder?.name ?: if (isRoot) stringResource(R.string.library_title) else "",
				onBack = if (isRoot) null else onBack,
				backLabel = stringResource(R.string.back),
			)
		}
		fullWidth("key") {
			KeyCard(
				books = counts?.books,
				folders = counts?.folders,
				importState = importState,
				emptyText = stringResource(if (isRoot) R.string.library_empty else R.string.library_folder_empty),
			)
		}
		fullWidth("actions") {
			Column(
				Modifier.padding(top = ReaderSpacing.space3),
				verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
			) {
				PrimaryButton(
					text = stringResource(R.string.library_import),
					onClick = { pick.launch(IMPORT_TYPES) },
					enabled = importState !is ImportState.Running,
					modifier = Modifier.fillMaxWidth(),
				)
				SecondaryButton(
					text = stringResource(R.string.library_new_folder),
					onClick = { dialog = LibraryDialog.NewFolder },
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
		(importState as? ImportState.Finished)?.let { finished ->
			fullWidth("import-result") { ImportSummary(finished, onDone = viewModel::dismissImport) }
		}
		if (isRoot) {
			fullWidth("search") { SearchField(query, viewModel::setQuery) }
		}
		if (searching) {
			fullWidth("results-head") { Section(stringResource(R.string.library_results)) }
			if (results.itemCount == 0 && results.loadState.refresh is LoadState.NotLoading) {
				fullWidth("no-results") { BodyText(stringResource(R.string.library_no_results)) }
			}
			bookTiles(results, tile)
		} else {
			if (folders.isNotEmpty()) {
				fullWidth("folders-head") { Section(stringResource(R.string.library_folders)) }
				items(folders, key = { "folder-${it.folder.id}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
					FolderRow(
						item = item,
						onOpen = { onOpenFolder(item.folder.id) },
						onActions = { dialog = LibraryDialog.FolderActions(item.folder) },
					)
				}
			}
			if (books.itemCount > 0) {
				fullWidth("books-head") { Section(stringResource(R.string.library_books)) }
				if (books.itemCount > 1) {
					fullWidth("sort") {
						val labels = LibrarySort.entries.associateWith { stringResource(it.label) }
						ReaderChips(
							options = LibrarySort.entries,
							selected = sort,
							onSelect = viewModel::setSort,
							label = labels::getValue,
							modifier = Modifier.padding(bottom = ReaderSpacing.space2),
						)
					}
				}
				bookTiles(books, tile)
			}
		}
	}

	LibraryDialogs(dialog, viewModel, onDismiss = { dialog = null }, open = { dialog = it })
}

@Composable
private fun LibraryDialogs(
	dialog: LibraryDialog?,
	viewModel: LibraryViewModel,
	onDismiss: () -> Unit,
	open: (LibraryDialog) -> Unit,
) {
	val tree by viewModel.folderTree.collectAsStateWithLifecycle()
	val scope = rememberCoroutineScope()
	when (dialog) {
		null -> Unit
		LibraryDialog.NewFolder -> FolderNameDialog(
			title = stringResource(R.string.library_new_folder),
			initial = "",
			confirmLabel = stringResource(R.string.create),
			onDismiss = onDismiss,
			onConfirm = viewModel::createFolder,
		)
		is LibraryDialog.FolderActions -> ActionsDialog(
			title = dialog.folder.name,
			actions = listOf(
				stringResource(R.string.rename) to { open(LibraryDialog.RenameFolder(dialog.folder)) },
				stringResource(R.string.move) to { open(LibraryDialog.MoveFolder(dialog.folder)) },
			),
			onDelete = {
				scope.launch {
					val counts = viewModel.countsOf(dialog.folder.id)
					open(LibraryDialog.DeleteFolder(dialog.folder, counts.books, counts.folders))
				}
			},
			onDismiss = onDismiss,
		)
		is LibraryDialog.RenameFolder -> FolderNameDialog(
			title = stringResource(R.string.rename),
			initial = dialog.folder.name,
			confirmLabel = stringResource(R.string.save),
			onDismiss = onDismiss,
			onConfirm = { viewModel.renameFolder(dialog.folder.id, it) },
		)
		is LibraryDialog.MoveFolder -> MoveDialog(
			tree = tree,
			currentFolderId = dialog.folder.parentId,
			movingFolderId = dialog.folder.id,
			onDismiss = onDismiss,
			onMove = { viewModel.moveFolder(dialog.folder.id, it) },
		)
		is LibraryDialog.DeleteFolder -> DeleteDialog(
			title = stringResource(R.string.delete_folder_title),
			body = if (dialog.books == 0 && dialog.folders == 0) {
				stringResource(R.string.delete_folder_empty)
			} else {
				stringResource(
					R.string.delete_folder_body,
					pluralStringResource(R.plurals.count_books, dialog.books, dialog.books),
					pluralStringResource(R.plurals.count_folders, dialog.folders, dialog.folders),
				)
			},
			onDismiss = onDismiss,
			onConfirm = { viewModel.deleteFolder(dialog.folder.id) },
		)
		is LibraryDialog.BookActions -> ActionsDialog(
			title = dialog.book.title,
			actions = listOf(
				stringResource(R.string.edit_details) to { open(LibraryDialog.EditBook(dialog.book)) },
				stringResource(R.string.move) to { open(LibraryDialog.MoveBook(dialog.book)) },
			),
			onDelete = { open(LibraryDialog.DeleteBook(dialog.book)) },
			onDismiss = onDismiss,
		)
		is LibraryDialog.EditBook -> BookDetailsDialog(
			book = dialog.book,
			onDismiss = onDismiss,
			onSave = { title, author -> viewModel.updateBook(dialog.book.id, title, author) },
		)
		is LibraryDialog.MoveBook -> MoveDialog(
			tree = tree,
			currentFolderId = dialog.book.folderId,
			onDismiss = onDismiss,
			onMove = { viewModel.moveBook(dialog.book.id, it) },
		)
		is LibraryDialog.DeleteBook -> DeleteDialog(
			title = stringResource(R.string.delete_book_title),
			body = stringResource(R.string.delete_book_body),
			onDismiss = onDismiss,
			onConfirm = { viewModel.deleteBook(dialog.book.id) },
		)
	}
}

/** The key card: the library's figures, and the import while it runs. */
@Composable
private fun KeyCard(books: Int?, folders: Int?, importState: ImportState, emptyText: String) {
	SweepKeyCard {
		WindowPlate(
			label = stringResource(R.string.library_books),
			figure = books?.toString().orEmpty(),
			plateLabel = stringResource(R.string.library_folders),
			plateValue = folders?.toString().orEmpty(),
		)
		when {
			importState is ImportState.Running ->
				LiveStatus(stringResource(R.string.importing, importState.done + 1, importState.total))
			books == 0 && folders == 0 -> BodyText(emptyText)
		}
	}
}

@Composable
private fun ImportSummary(state: ImportState.Finished, onDone: () -> Unit) {
	Column(
		Modifier.padding(top = ReaderSpacing.space3),
		verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space3),
	) {
		val parts = buildList {
			if (state.imported > 0) add(pluralStringResource(R.plurals.import_imported, state.imported, state.imported))
			if (state.duplicates > 0) {
				add(pluralStringResource(R.plurals.import_duplicates, state.duplicates, state.duplicates))
			}
		}
		BodyText(if (parts.isEmpty()) stringResource(R.string.import_nothing) else parts.joinToString(" "))
		state.failures.take(MAX_FAILURES_SHOWN).forEach { failure ->
			ErrorNotice(failureMessage(failure))
		}
		val more = state.failures.size - MAX_FAILURES_SHOWN
		if (more > 0) BodyText(pluralStringResource(R.plurals.import_more_failures, more, more))
		SecondaryButton(stringResource(R.string.import_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
	}
}

@Composable
private fun failureMessage(failure: ImportFailure): String = stringResource(
	when (failure.reason) {
		ImportFailure.Reason.UNSUPPORTED -> R.string.import_failed_unsupported
		ImportFailure.Reason.RAR -> R.string.import_failed_rar
		ImportFailure.Reason.UNREADABLE -> R.string.import_failed_unreadable
		ImportFailure.Reason.NO_SPACE -> R.string.import_failed_no_space
		ImportFailure.Reason.LOCKED -> R.string.import_failed_locked
		ImportFailure.Reason.FAILED -> R.string.import_failed_other
	},
	failure.fileName,
)

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
	ReaderTextField(
		value = query,
		onValueChange = onChange,
		label = stringResource(R.string.library_search),
		placeholder = stringResource(R.string.library_search_hint),
		leadingIcon = ReaderIcons.Search,
		imeAction = ImeAction.Search,
		trailing = if (query.isEmpty()) {
			null
		} else {
			{
				val clear = stringResource(R.string.library_search_clear)
				Box(
					modifier = Modifier
						.size(ReaderSize.targetMin)
						.clickable(onClickLabel = clear) { onChange("") }
						.semantics { contentDescription = clear },
					contentAlignment = Alignment.Center,
				) {
					ReaderIcon(ReaderIcons.Close)
				}
			}
		},
		modifier = Modifier.padding(top = ReaderSpacing.space6),
	)
}

@Composable
private fun Section(text: String) {
	SectionHeadline(text, Modifier.padding(top = ReaderSpacing.space6, bottom = ReaderSpacing.space1))
}

@Composable
private fun FolderRow(item: FolderItem, onOpen: () -> Unit, onActions: () -> Unit) {
	ReaderRow(
		title = item.folder.name,
		trailing = item.itemCount.toString(),
		leadingIcon = ReaderIcons.Folder,
		onClick = onOpen,
		onLongClick = onActions,
		onLongClickLabel = stringResource(R.string.show_actions),
	)
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
	item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

private fun LazyGridScope.bookTiles(items: LazyPagingItems<Book>, tile: @Composable (Book) -> Unit) {
	items(
		count = items.itemCount,
		key = items.itemKey { "book-${it.id}" },
		contentType = { "book" },
	) { index ->
		val book = items[index]
		if (book == null) CoverTilePlaceholder() else tile(book)
	}
}

private val LibrarySort.label: Int
	get() = when (this) {
		LibrarySort.TITLE -> R.string.sort_title
		LibrarySort.RECENT -> R.string.sort_recent
		LibrarySort.ADDED -> R.string.sort_added
	}

/**
 * MIME types for the picker. Comics and EPUBs often arrive as generic zip or octet-stream,
 * so those are included; the importer checks the bytes either way. RAR comics stay pickable
 * so the person is told why they are refused rather than finding them greyed out.
 */
private val IMPORT_TYPES = arrayOf(
	"application/pdf",
	"application/epub+zip",
	"application/vnd.comicbook+zip",
	"application/x-cbz",
	"application/x-cb7",
	"application/x-7z-compressed",
	"application/zip",
	"application/octet-stream",
	"text/plain",
	"application/vnd.comicbook-rar",
	"application/x-cbr",
	"application/vnd.rar",
	"application/x-rar-compressed",
)

/** Covers decode at this multiple of the narrowest tile, enough for wider columns. */
private const val COVER_SCALE = 2
private const val MAX_FAILURES_SHOWN = 3
