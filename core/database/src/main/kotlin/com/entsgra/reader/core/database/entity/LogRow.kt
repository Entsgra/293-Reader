package com.entsgra.reader.core.database.entity

import androidx.room.Embedded
import com.entsgra.reader.core.model.LogEntry

/** A sitting joined with its book's title, ebook or physical. */
data class LogRow(
	@Embedded val session: ReadingSessionEntity,
	val title: String,
)

fun LogRow.asModel() = LogEntry(session.asModel(), title)
