package com.entsgra.reader.feature.log

import com.entsgra.reader.core.model.LogEntry
import com.entsgra.reader.core.model.ReadingItem
import com.entsgra.reader.core.model.ReadingSession
import com.entsgra.reader.core.screenshots.SCREENSHOT_QUALIFIERS
import com.entsgra.reader.core.screenshots.SCREENSHOT_SDK
import com.entsgra.reader.core.screenshots.ScreenshotClock
import com.entsgra.reader.core.screenshots.captureScreen
import com.entsgra.reader.core.screenshots.screenshotRule
import com.entsgra.reader.core.screenshots.setScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The README's log: a week of ebook and paper sittings, newest first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_QUALIFIERS)
class LogScreenshotTest {
	@get:Rule
	val compose = screenshotRule()

	private val today = LocalDate.now(ScreenshotClock)

	private class Sitting(val daysAgo: Long, val at: LocalTime, val minutes: Long, val title: String, val item: ReadingItem)

	private val sittings = listOf(
		Sitting(0, LocalTime.of(16, 10), 35, "Meditations", ReadingItem.Physical(1)),
		Sitting(0, LocalTime.of(8, 5), 22, "Das Kapital", ReadingItem.Ebook(1)),
		Sitting(1, LocalTime.of(21, 30), 48, "The Republic", ReadingItem.Physical(2)),
		Sitting(1, LocalTime.of(13, 15), 12, "Tao Te Ching", ReadingItem.Ebook(6)),
		Sitting(2, LocalTime.of(22, 0), 31, "An Introduction to Buddhism: Teachings, History and Practices", ReadingItem.Ebook(3)),
		Sitting(3, LocalTime.of(20, 40), 41, "Das Kapital", ReadingItem.Ebook(1)),
		Sitting(3, LocalTime.of(7, 50), 18, "Meditations", ReadingItem.Physical(1)),
		Sitting(4, LocalTime.of(12, 30), 9, "The Dhammapada", ReadingItem.Ebook(7)),
		Sitting(5, LocalTime.of(21, 5), 55, "The Republic", ReadingItem.Physical(2)),
		Sitting(6, LocalTime.of(19, 20), 26, "Bhagavad Gita", ReadingItem.Ebook(11)),
	)

	@Test
	fun log() {
		val entries = sittings.mapIndexed { index, sitting ->
			val start = today.minusDays(sitting.daysAgo).atTime(sitting.at).toInstant(ZoneOffset.UTC).toEpochMilli()
			LogEntry(
				ReadingSession(index + 1L, sitting.item, startedAt = start, endedAt = start + sitting.minutes * 60_000, startPage = 0, endPage = 0),
				sitting.title,
			)
		}
		val rows = entries.groupBy { epochDay(it.session.startedAt, ZoneOffset.UTC) }
			.flatMap { (day, sittings) -> listOf(LogItem.Day(day)) + sittings.map(LogItem::Sitting) }
		val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
		val totals = ReadingTotals(
			weekMs = entries.filter { epochDay(it.session.startedAt, ZoneOffset.UTC) >= monday }.sumOf { it.durationMs },
			todayMs = entries.filter { epochDay(it.session.startedAt, ZoneOffset.UTC) == today.toEpochDay() }.sumOf { it.durationMs },
		)

		compose.setScreen {
			LogContent(
				totals = totals,
				count = rows.size,
				item = rows::get,
				key = { it },
				contentType = { null },
				empty = false,
				onOpen = {},
				today = today,
			)
		}
		compose.captureScreen("log")
	}
}
