package com.entsgra.reader.reader.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NaturalOrderTest {
	private fun sorted(vararg names: String) = names.sortedWith(NaturalOrder)

	@Test
	fun numbersCompareByValue() {
		assertThat(sorted("page10.jpg", "page2.jpg", "page1.jpg"))
			.containsExactly("page1.jpg", "page2.jpg", "page10.jpg").inOrder()
	}

	@Test
	fun caseIsIgnored() {
		assertThat(sorted("B.png", "a.png", "C.png")).containsExactly("a.png", "B.png", "C.png").inOrder()
	}

	@Test
	fun leadingZerosSortWithTheirValue() {
		assertThat(sorted("010.jpg", "9.jpg", "001.jpg")).containsExactly("001.jpg", "9.jpg", "010.jpg").inOrder()
	}

	@Test
	fun foldersSortBeforeTheirLaterSiblings() {
		assertThat(sorted("ch2/01.jpg", "ch10/01.jpg", "ch1/02.jpg", "ch1/01.jpg"))
			.containsExactly("ch1/01.jpg", "ch1/02.jpg", "ch2/01.jpg", "ch10/01.jpg").inOrder()
	}

	@Test
	fun longDigitRunsDoNotOverflow() {
		assertThat(sorted("p99999999999999999999.jpg", "p100000000000000000000.jpg"))
			.containsExactly("p99999999999999999999.jpg", "p100000000000000000000.jpg").inOrder()
	}

	@Test
	fun isConsistentForEqualValues() {
		assertThat(NaturalOrder.compare("a1", "a01")).isNotEqualTo(0)
		assertThat(NaturalOrder.compare("a1", "a1")).isEqualTo(0)
	}
}
