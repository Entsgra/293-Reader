package com.entsgra.reader.feature.reader

import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.ReadingTextSize
import com.entsgra.reader.core.model.SpreadMode
import com.entsgra.reader.core.screenshots.SCREENSHOT_QUALIFIERS
import com.entsgra.reader.core.screenshots.SCREENSHOT_SDK
import com.entsgra.reader.core.screenshots.captureScreen
import com.entsgra.reader.core.screenshots.screenshotRule
import com.entsgra.reader.core.screenshots.setScreen
import com.entsgra.reader.reader.api.BookMetadata
import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowDocument
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.PageSize
import com.entsgra.reader.reader.api.TextSpan
import com.entsgra.reader.reader.api.TocEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The README's EPUB reader: Meditations, partway through the first book, controls shown as it opens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_QUALIFIERS)
class ReaderScreenshotTest {
	@get:Rule
	val compose = screenshotRule()

	private val document = MeditationsDocument()

	private val book = Book(
		id = 5,
		folderId = null,
		title = "Meditations",
		author = "Marcus Aurelius",
		format = BookFormatId.EPUB,
		fileName = "book-5",
		fileSize = 0,
		contentHash = "",
		coverFileName = null,
		pageCount = null,
		readingDirection = ReadingDirection.LTR,
		spreadMode = SpreadMode.AUTO,
		addedAt = 0,
		lastOpenedAt = null,
	)

	@Test
	fun reader() {
		// Open on the page holding the Rusticus paragraph, partway through the first book.
		val offset = document.offsetOf("From Rusticus")
		compose.setScreen {
			FlowReader(
				document = document,
				book = book,
				position = Locator.Text(chapter = 0, offset = offset),
				textSize = ReadingTextSize.MEDIUM,
				onPositionChange = { _, _ -> },
				onTextSize = {},
				onBack = {},
			)
		}
		// Chapters are laid out off the main thread; wait for the page to arrive.
		compose.waitUntil(LAYOUT_TIMEOUT_MS) {
			compose.onAllNodes(hasText("Rusticus", substring = true)).fetchSemanticsNodes().isNotEmpty()
		}
		compose.captureScreen("reader-controls")
	}

	private companion object {
		const val LAYOUT_TIMEOUT_MS = 10_000L
	}
}

/**
 * The opening of Meditations, George Long's translation (public domain): the first book and the
 * start of the second. Later books have their headings only.
 */
private class MeditationsDocument : FlowDocument {
	private val books = listOf(
		"The First Book", "The Second Book", "The Third Book", "The Fourth Book", "The Fifth Book", "The Sixth Book",
		"The Seventh Book", "The Eighth Book", "The Ninth Book", "The Tenth Book", "The Eleventh Book", "The Twelfth Book",
	)

	private val chapters: List<FlowContent> = listOf(
		chapter(books[0], FIRST_BOOK),
		chapter(books[1], SECOND_BOOK),
	) + books.drop(2).map { chapter(it, emptyList()) }

	override val metadata = BookMetadata(title = "Meditations", author = "Marcus Aurelius")
	override val contents = books.mapIndexed { index, title -> TocEntry(title, FlowLink(index), depth = 0) }
	override val chapterCount = books.size
	override val chapterWeights = listOf(9_000L, 7_000, 8_000, 16_000, 14_000, 15_000, 16_000, 15_000, 13_000, 14_000, 13_000, 9_000)
	override val language = "en"

	override suspend fun chapter(index: Int) = chapters[index]
	override suspend fun imageSize(key: String): PageSize? = null
	override suspend fun image(key: String, maxWidth: Int, maxHeight: Int): Bitmap? = null
	override suspend fun cover(maxWidth: Int, maxHeight: Int): Bitmap? = null
	override fun close() = Unit

	/** Where [text] starts in the first book. */
	fun offsetOf(text: String): Int {
		var offset = 0
		for (block in chapters[0].blocks) {
			val spans = (block as? FlowBlock.Paragraph)?.spans?.joinToString("") { it.text }
			if (spans != null && spans.startsWith(text)) return offset
			offset += block.length
		}
		error("$text is not in the first book")
	}

	private fun chapter(title: String, paragraphs: List<String>) = FlowContent(
		listOf(FlowBlock.Heading(1, listOf(TextSpan(title)))) + paragraphs.map { FlowBlock.Paragraph(listOf(TextSpan(it))) },
	)
}

private val FIRST_BOOK = listOf(
	"From my grandfather Verus I learned good morals and the government of my temper.",
	"From the reputation and remembrance of my father, modesty and a manly character.",
	"From my mother, piety and beneficence, and abstinence, not only from evil deeds, but even from evil thoughts; " +
		"and further, simplicity in my way of living, far removed from the habits of the rich.",
	"From my great-grandfather, not to have frequented public schools, and to have had good teachers at home, and to " +
		"know that on such things a man should spend liberally.",
	"From my governor, to be neither of the green nor of the blue party at the games in the Circus, nor a partizan " +
		"either of the Parmularius or the Scutarius at the gladiators' fights; from him too I learned endurance of " +
		"labour, and to want little, and to work with my own hands, and not to meddle with other people's affairs, and " +
		"not to be ready to listen to slander.",
	"From Diognetus, not to busy myself about trifling things, and not to give credit to what was said by " +
		"miracle-workers and jugglers about incantations and the driving away of daemons and such things; and not to " +
		"breed quails for fighting, nor to give myself up passionately to such things; and to endure freedom of " +
		"speech; and to have become intimate with philosophy; and to have been a hearer, first of Bacchius, then of " +
		"Tandasis and Marcianus; and to have written dialogues in my youth; and to have desired a plank bed and skin, " +
		"and whatever else of the kind belongs to the Grecian discipline.",
	"From Rusticus I received the impression that my character required improvement and discipline; and from him I " +
		"learned not to be led astray to sophistic emulation, nor to writing on speculative matters, nor to delivering " +
		"little hortatory orations, nor to showing myself off as a man who practises much discipline, or does " +
		"benevolent acts in order to make a display; and to abstain from rhetoric, and poetry, and fine writing; and " +
		"not to walk about in the house in my outdoor dress, nor to do other things of the kind; and to write my " +
		"letters with simplicity, like the letter which Rusticus wrote from Sinuessa to my mother; and with respect to " +
		"those who have offended me by words, or done me wrong, to be easily disposed to be pacified and reconciled, " +
		"as soon as they have shown a readiness to be reconciled; and to read carefully, and not to be satisfied with " +
		"a superficial understanding of a book; nor hastily to give my assent to those who talk overmuch; and I am " +
		"indebted to him for being acquainted with the discourses of Epictetus, which he communicated to me out of " +
		"his own collection.",
	"From Apollonius I learned freedom of will and undeviating steadiness of purpose; and to look to nothing else, " +
		"not even for a moment, except to reason; and to be always the same, in sharp pains, on the occasion of the " +
		"loss of a child, and in long illness; and to see clearly in a living example that the same man can be both " +
		"most resolute and yielding, and not peevish in giving his instruction; and to have had before my eyes a man " +
		"who clearly considered his experience and his skill in expounding philosophical principles as the smallest " +
		"of his merits; and from him I learned how to receive from friends what are esteemed favours, without being " +
		"either humbled by them or letting them pass unnoticed.",
	"From Sextus, a benevolent disposition, and the example of a family governed in a fatherly manner, and the idea " +
		"of living conformably to nature; and gravity without affectation, and to look carefully after the interests " +
		"of friends, and to tolerate ignorant persons, and those who form opinions without consideration: he had the " +
		"power of readily accommodating himself to all, so that intercourse with him was more agreeable than any " +
		"flattery; and at the same time he was most highly venerated by those who associated with him: and he had the " +
		"faculty both of discovering and ordering, in an intelligent and methodical way, the principles necessary for " +
		"life; and he never showed anger or any other passion, but was entirely free from passion, and also most " +
		"affectionate; and he could express approbation without noisy display, and he possessed much knowledge " +
		"without ostentation.",
)

private val SECOND_BOOK = listOf(
	"Begin the morning by saying to thyself, I shall meet with the busy-body, the ungrateful, arrogant, deceitful, " +
		"envious, unsocial. All these things happen to them by reason of their ignorance of what is good and evil.",
)
