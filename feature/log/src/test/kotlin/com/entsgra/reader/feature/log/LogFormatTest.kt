package com.entsgra.reader.feature.log

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class LogFormatTest {
	@Test
	fun hoursAndMinutesRoundDown() {
		assertThat(hoursMinutes(0)).isEqualTo("0:00")
		assertThat(hoursMinutes(59_999)).isEqualTo("0:00")
		assertThat(hoursMinutes(65 * 60_000L)).isEqualTo("1:05")
		assertThat(hoursMinutes(25 * 3_600_000L)).isEqualTo("25:00")
	}

	@Test
	fun sittingsRoundToTheNearestMinuteAndShowAtLeastOne() {
		assertThat(sittingMinutes(30_000)).isEqualTo(1)
		assertThat(sittingMinutes(89_999)).isEqualTo(1)
		assertThat(sittingMinutes(90_000)).isEqualTo(2)
		assertThat(sittingMinutes(42 * 60_000L)).isEqualTo(42)
	}

	@Test
	fun daysFollowLocalMidnight() {
		val tokyo = ZoneId.of("Asia/Tokyo")
		val lateEvening = ZonedDateTime.of(2026, 10, 28, 23, 30, 0, 0, tokyo).toInstant().toEpochMilli()
		val nextMorning = ZonedDateTime.of(2026, 10, 29, 0, 30, 0, 0, tokyo).toInstant().toEpochMilli()
		assertThat(epochDay(nextMorning, tokyo) - epochDay(lateEvening, tokyo)).isEqualTo(1)
	}
}
