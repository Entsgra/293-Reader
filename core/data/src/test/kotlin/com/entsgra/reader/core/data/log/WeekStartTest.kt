package com.entsgra.reader.core.data.log

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class WeekStartTest {
	private val berlin = ZoneId.of("Europe/Berlin")

	private fun at(year: Int, month: Int, day: Int, hour: Int = 0) =
		ZonedDateTime.of(year, month, day, hour, 0, 0, 0, berlin).toInstant().toEpochMilli()

	@Test
	fun weekStartsAtMondayMidnightLocalTime() {
		// Wednesday 28 October 2026, 16:00.
		assertThat(weekStart(at(2026, 10, 28, 16), berlin)).isEqualTo(at(2026, 10, 26))
	}

	@Test
	fun mondayIsItsOwnWeekStartAndSundayBelongsToTheWeekBefore() {
		assertThat(weekStart(at(2026, 10, 26, 9), berlin)).isEqualTo(at(2026, 10, 26))
		assertThat(weekStart(at(2026, 10, 25, 23), berlin)).isEqualTo(at(2026, 10, 19))
	}

	@Test
	fun clockChangeWeekStillStartsAtLocalMidnight() {
		// Summer time ended on Sunday 25 October 2026; the week before still starts at 00:00 CEST.
		assertThat(weekStart(at(2026, 10, 25, 12), berlin)).isEqualTo(at(2026, 10, 19))
	}
}
