package com.entsgra.reader.core.data.log

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.entsgra.reader.core.data.library.visibleFolderIds
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.entity.LogRow
import com.entsgra.reader.core.database.entity.asModel
import com.entsgra.reader.core.model.LogEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The reading log: sittings with ebooks, recorded by the reader, and with physical books,
 * logged by hand, as one list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ReadingLogRepository @Inject constructor(
	private val database: DatabaseProvider,
) {
	/**
	 * Finished sittings, newest first. Follows folder locks as they change, so sittings with
	 * books in locked folders never show.
	 */
	fun entries(): Flow<PagingData<LogEntry>> = flow {
		val db = database.get()
		emitAll(
			db.folderDao().observeAll()
				.map(::visibleFolderIds)
				.distinctUntilChanged()
				.flatMapLatest { visible ->
					Pager(
						config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
						pagingSourceFactory = { db.readingSessionDao().pageLog(visible) },
					).flow
				}
				.map { page -> page.map(LogRow::asModel) },
		)
	}

	/** Milliseconds read since the start of this week (Monday, local time). */
	fun readThisWeek(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Flow<Long> = flow {
		emitAll(database.get().readingSessionDao().observeReadingMs(weekStart(now, zone)))
	}

	private companion object {
		const val PAGE_SIZE = 50
	}
}

/** Midnight at the start of the week holding [now], weeks starting on Monday. */
internal fun weekStart(now: Long, zone: ZoneId): Long =
	Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
		.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
		.atStartOfDay(zone)
		.toInstant()
		.toEpochMilli()
