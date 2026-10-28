package com.entsgra.reader.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.entsgra.reader.core.data.log.ReadingLogRepository
import com.entsgra.reader.core.model.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Milliseconds read this week and today. */
data class ReadingTotals(val weekMs: Long, val todayMs: Long)

/** A row of the log: a day heading, or a sitting under it. */
sealed interface LogItem {
	data class Day(val epochDay: Long) : LogItem
	data class Sitting(val entry: LogEntry) : LogItem
}

@HiltViewModel
class LogViewModel @Inject constructor(
	log: ReadingLogRepository,
) : ViewModel() {
	private val zone = ZoneId.systemDefault()

	val items: Flow<PagingData<LogItem>> = log.entries()
		.map { page -> withDays(page.map { LogItem.Sitting(it) }, zone) }
		.cachedIn(viewModelScope)

	/** Time read this week (since Monday) and today; null while the database opens. */
	val totals: StateFlow<ReadingTotals?> = combine(log.readThisWeek(zone = zone), log.readToday(zone = zone), ::ReadingTotals)
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

	private companion object {
		const val STOP_MS = 5_000L
	}
}

/** Puts a [LogItem.Day] heading before the first sitting of each local day. */
internal fun withDays(page: PagingData<LogItem.Sitting>, zone: ZoneId): PagingData<LogItem> =
	page.insertSeparators { before: LogItem.Sitting?, after: LogItem.Sitting? ->
		val day = after?.let { epochDay(it.entry.session.startedAt, zone) }
		when {
			day == null -> null
			before == null || epochDay(before.entry.session.startedAt, zone) != day -> LogItem.Day(day)
			else -> null
		}
	}

internal fun epochDay(epochMillis: Long, zone: ZoneId): Long =
	Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

/** "1:05" for 65 minutes: hours and minutes, rounded down. */
internal fun hoursMinutes(ms: Long): String {
	val minutes = (ms / 60_000).coerceAtLeast(0)
	return "%d:%02d".format(minutes / 60, minutes % 60)
}

/** A sitting's length in whole minutes, at least one: the log only keeps sittings of 30 s or more. */
internal fun sittingMinutes(ms: Long): Int = ((ms + 30_000) / 60_000).toInt().coerceAtLeast(1)
