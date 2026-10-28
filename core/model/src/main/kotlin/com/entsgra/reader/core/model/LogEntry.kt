package com.entsgra.reader.core.model

/**
 * A finished sitting in the reading log with the title of its book. Ebook sittings come from
 * the reader (30 seconds or more); physical-book sittings are logged by hand. Both share
 * [ReadingSession], so the log reads them as one list.
 */
data class LogEntry(val session: ReadingSession, val title: String) {
	val durationMs: Long get() = (session.endedAt ?: session.startedAt) - session.startedAt
}
