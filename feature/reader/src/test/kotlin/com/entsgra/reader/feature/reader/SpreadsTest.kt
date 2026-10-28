package com.entsgra.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpreadsTest {
	@Test
	fun singleShowsOnePageEach() {
		assertThat(spreadsOf(3, double = false)).containsExactly(0..0, 1..1, 2..2).inOrder()
	}

	@Test
	fun doubleKeepsTheCoverAloneThenPairs() {
		assertThat(spreadsOf(5, double = true)).containsExactly(0..0, 1..2, 3..4).inOrder()
	}

	@Test
	fun doubleLeavesAnOddLastPageAlone() {
		assertThat(spreadsOf(4, double = true)).containsExactly(0..0, 1..2, 3..3).inOrder()
	}

	@Test
	fun emptyAndOnePageBooks() {
		assertThat(spreadsOf(0, double = true)).isEmpty()
		assertThat(spreadsOf(1, double = true)).containsExactly(0..0)
	}

	@Test
	fun findsTheSpreadForAPage() {
		val spreads = spreadsOf(6, double = true)
		assertThat(spreads.indexOfPage(0)).isEqualTo(0)
		assertThat(spreads.indexOfPage(2)).isEqualTo(1)
		assertThat(spreads.indexOfPage(5)).isEqualTo(3)
		assertThat(spreads.indexOfPage(99)).isEqualTo(3)
		assertThat(spreads.indexOfPage(-1)).isEqualTo(0)
	}
}
