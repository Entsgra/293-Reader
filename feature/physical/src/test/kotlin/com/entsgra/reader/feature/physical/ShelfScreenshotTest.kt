package com.entsgra.reader.feature.physical

import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingStatus
import com.entsgra.reader.core.screenshots.SCREENSHOT_QUALIFIERS
import com.entsgra.reader.core.screenshots.SCREENSHOT_SDK
import com.entsgra.reader.core.screenshots.captureScreen
import com.entsgra.reader.core.screenshots.screenshotRule
import com.entsgra.reader.core.screenshots.setScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The README's shelf: a paper book on the key card, the rest by status. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_QUALIFIERS)
class ShelfScreenshotTest {
	@get:Rule
	val compose = screenshotRule()

	/** Most recently touched first, as the repository returns them. */
	private val books = listOf(
		PhysicalBook(1, "Meditations", "Marcus Aurelius", 254, null, ReadingStatus.READING, 87, 0, 9),
		PhysicalBook(2, "The Republic", "Plato", 416, null, ReadingStatus.READING, 132, 0, 8),
		PhysicalBook(3, "Thus Spoke Zarathustra", "Friedrich Nietzsche", 352, null, ReadingStatus.WANT_TO_READ, 0, 0, 7),
		PhysicalBook(4, "The Prince", "Niccolò Machiavelli", 140, null, ReadingStatus.WANT_TO_READ, 0, 0, 6),
		PhysicalBook(5, "Tao Te Ching", "Laozi", 96, null, ReadingStatus.FINISHED, 96, 0, 5),
		PhysicalBook(6, "The Communist Manifesto", "Karl Marx and Friedrich Engels", 64, null, ReadingStatus.FINISHED, 64, 0, 4),
		PhysicalBook(7, "The Wealth of Nations", "Adam Smith", 1264, null, ReadingStatus.ABANDONED, 212, 0, 3),
	)

	@Test
	fun shelf() {
		compose.setScreen {
			ShelfContentView(shelfContent(books), onAdd = {}, onOpenBook = {})
		}
		compose.captureScreen("shelf")
	}
}
