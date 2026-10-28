package com.entsgra.reader.feature.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.LogEntry
import com.entsgra.reader.core.model.ReadingItem
import com.entsgra.reader.core.model.ReadingSession
import java.time.LocalDate

private fun sitting(id: Long, item: ReadingItem, title: String, daysAgo: Long, minutes: Long): LogItem.Sitting {
	val day = LocalDate.now().minusDays(daysAgo).toEpochDay() * 86_400_000 + 43_200_000
	return LogItem.Sitting(
		LogEntry(ReadingSession(id, item, startedAt = day, endedAt = day + minutes * 60_000, startPage = 0, endPage = 0), title),
	)
}

private val sample: List<LogItem> = listOf(
	LogItem.Day(LocalDate.now().toEpochDay()),
	sitting(1, ReadingItem.Ebook(1), "The Rings of Saturn", 0, 42),
	sitting(2, ReadingItem.Physical(1), "Austerlitz", 0, 25),
	LogItem.Day(LocalDate.now().minusDays(1).toEpochDay()),
	sitting(3, ReadingItem.Ebook(2), "Vertigo", 1, 1),
	LogItem.Day(LocalDate.now().minusDays(4).toEpochDay()),
	sitting(4, ReadingItem.Physical(2), "The Emigrants", 4, 95),
)

@Composable
private fun Log(theme: AppTheme) = ReaderTheme(theme) {
	Box(Modifier.background(ReaderTheme.colors.fog)) {
		LogContent(
			totals = ReadingTotals(weekMs = 163 * 60_000L, todayMs = 67 * 60_000L),
			count = sample.size,
			item = sample::get,
			key = { it },
			contentType = { null },
			empty = false,
			onOpen = {},
		)
	}
}

@Composable
private fun EmptyLog(theme: AppTheme) = ReaderTheme(theme) {
	Box(Modifier.background(ReaderTheme.colors.fog)) {
		LogContent(ReadingTotals(0, 0), count = 0, item = { null }, key = { it }, contentType = { null }, empty = true, onOpen = {})
	}
}

@Preview(name = "Log light", widthDp = 360, heightDp = 900)
@Composable
private fun LogLight() = Log(AppTheme.LIGHT)

@Preview(name = "Log dark", widthDp = 360, heightDp = 900)
@Composable
private fun LogDark() = Log(AppTheme.DARK)

@Preview(name = "Log light yellow", widthDp = 360, heightDp = 900)
@Composable
private fun LogYellow() = Log(AppTheme.YELLOW)

@Preview(name = "Log light pink", widthDp = 360, heightDp = 900)
@Composable
private fun LogPink() = Log(AppTheme.PINK)

@Preview(name = "Empty log light", widthDp = 360, heightDp = 640)
@Composable
private fun EmptyLogLight() = EmptyLog(AppTheme.LIGHT)

@Preview(name = "Empty log dark", widthDp = 360, heightDp = 640)
@Composable
private fun EmptyLogDark() = EmptyLog(AppTheme.DARK)
