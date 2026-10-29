package com.entsgra.reader.feature.log

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.entsgra.reader.core.designsystem.component.BodyText
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SectionHeadline
import com.entsgra.reader.core.designsystem.component.SweepKeyCard
import com.entsgra.reader.core.designsystem.component.WindowPlate
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.model.LogEntry
import com.entsgra.reader.core.model.ReadingItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun LogScreen(
	onOpenEbook: (Long) -> Unit,
	onOpenPhysical: (Long) -> Unit,
	viewModel: LogViewModel = hiltViewModel(),
) {
	val items = viewModel.items.collectAsLazyPagingItems()
	val totals by viewModel.totals.collectAsStateWithLifecycle()
	val loaded = items.loadState.refresh is LoadState.NotLoading
	LogContent(
		totals = totals,
		count = items.itemCount,
		item = { items[it] },
		key = items.itemKey { it.key() },
		contentType = items.itemContentType { it::class },
		empty = loaded && items.itemCount == 0,
		onOpen = { entry -> open(entry, onOpenEbook, onOpenPhysical) },
	)
}

/**
 * The reading log: time read this week and today on the key card, then every sitting by day, newest first.
 * Ebook and paper-book sittings sit side by side; a row opens its book.
 */
@Composable
internal fun LogContent(
	totals: ReadingTotals?,
	count: Int,
	item: (Int) -> LogItem?,
	key: (Int) -> Any,
	contentType: (Int) -> Any?,
	empty: Boolean,
	onOpen: (LogEntry) -> Unit,
) {
	val today = LocalDate.now().toEpochDay()
	LazyColumn(
		Modifier
			.fillMaxSize()
			.padding(horizontal = ReaderSpacing.space4),
	) {
		item { ScreenTitle(stringResource(R.string.log_title)) }
		if (totals != null) {
			item {
				SweepKeyCard {
					WindowPlate(
						label = stringResource(R.string.log_this_week),
						figure = hoursMinutes(totals.weekMs),
						plateLabel = stringResource(R.string.log_today),
						plateValue = hoursMinutes(totals.todayMs),
					)
				}
			}
		}
		if (empty) item { BodyText(stringResource(R.string.log_empty), Modifier.padding(top = ReaderSpacing.space6)) }
		items(count, key = key, contentType = contentType) { index ->
			when (val row = item(index)) {
				null -> Unit
				is LogItem.Day -> SectionHeadline(
					dayLabel(row.epochDay, today),
					Modifier.padding(top = ReaderSpacing.space8, bottom = ReaderSpacing.space2),
				)
				is LogItem.Sitting -> ReaderRow(
					title = row.entry.title,
					trailing = stringResource(R.string.log_minutes, sittingMinutes(row.entry.durationMs)),
					onClick = { onOpen(row.entry) },
				)
			}
		}
		item { Spacer(Modifier.padding(bottom = ReaderSpacing.space8)) }
	}
}

@Composable
private fun dayLabel(epochDay: Long, today: Long): String = when (epochDay) {
	today -> stringResource(R.string.log_today)
	today - 1 -> stringResource(R.string.log_yesterday)
	else -> {
		val date = LocalDate.ofEpochDay(epochDay)
		val pattern = if (date.year == LocalDate.now().year) "EEE, MMM d" else "MMM d, yyyy"
		date.format(DateTimeFormatter.ofPattern(pattern))
	}
}

private fun open(entry: LogEntry, onOpenEbook: (Long) -> Unit, onOpenPhysical: (Long) -> Unit) {
	when (val book = entry.session.item) {
		is ReadingItem.Ebook -> onOpenEbook(book.bookId)
		is ReadingItem.Physical -> onOpenPhysical(book.physicalBookId)
	}
}

private fun LogItem.key(): Any = when (this) {
	is LogItem.Day -> "day-$epochDay"
	is LogItem.Sitting -> entry.session.id
}
