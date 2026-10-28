package com.entsgra.reader.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocatorTest {
	@Test
	fun pageRoundTrips() {
		val locator = Locator.Page(41)
		assertThat(Locator.decode(locator.encode())).isEqualTo(locator)
	}

	@Test
	fun textRoundTrips() {
		val locator = Locator.Text(chapter = 3, offset = 1200)
		assertThat(Locator.decode(locator.encode())).isEqualTo(locator)
	}

	@Test
	fun malformedDecodesToNull() {
		assertThat(Locator.decode("")).isNull()
		assertThat(Locator.decode("p:x")).isNull()
		assertThat(Locator.decode("t:1")).isNull()
		assertThat(Locator.decode("q:1")).isNull()
	}

	@Test
	fun formatFromExtensionIgnoresCase() {
		assertThat(BookFormatId.fromExtension("CBZ")).isEqualTo(BookFormatId.CBZ)
		assertThat(BookFormatId.fromExtension("cbr")).isNull()
	}
}
