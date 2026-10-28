package com.entsgra.reader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.entsgra.reader.core.designsystem.component.FieldLabel
import com.entsgra.reader.core.designsystem.component.ReaderChips
import com.entsgra.reader.core.designsystem.component.ReaderRow
import com.entsgra.reader.core.designsystem.component.ReaderScrubber
import com.entsgra.reader.core.designsystem.component.Rule
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.component.SecondaryButton
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.designsystem.theme.readingType
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.Locator
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.ReadingTextSize
import com.entsgra.reader.reader.api.FlowBlock
import com.entsgra.reader.reader.api.FlowContent
import com.entsgra.reader.reader.api.FlowDocument
import com.entsgra.reader.reader.api.FlowLink
import com.entsgra.reader.reader.api.TextSpan
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Page mode for reflowable books (EPUB, TXT). The text is laid out to the screen and the chosen
 * text size, a chapter at a time; turning past either end of a chapter moves into the next one.
 * Gestures match the paged reader: swipe or tap the outer thirds to turn, the middle for the
 * controls. Links inside the book jump to their target.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowReader(
	document: FlowDocument,
	book: Book,
	position: Locator.Text,
	textSize: ReadingTextSize,
	onPositionChange: (Locator.Text, Float) -> Unit,
	onTextSize: (ReadingTextSize) -> Unit,
	onBack: () -> Unit,
) {
	var controls by rememberSaveable { mutableStateOf(true) }
	var contents by rememberSaveable { mutableStateOf(false) }
	ImmersiveEffect(immersive = !controls && !contents)
	val latest by rememberUpdatedState(position)
	val rightToLeft = book.readingDirection == ReadingDirection.RTL
	val density = LocalDensity.current
	val fonts = LocalFontFamilyResolver.current
	val unreadable = stringResource(R.string.reader_chapter_unreadable)
	val progress = remember(document) { FlowProgress(document.chapterWeights) }

	// Insets that do not change as the bars hide, so hiding the controls never reflows the text.
	val padding = WindowInsets.systemBarsIgnoringVisibility
		.union(WindowInsets.displayCutout)
		.add(WindowInsets(ReaderSpacing.space6, ReaderSpacing.space6, ReaderSpacing.space6, ReaderSpacing.space6))
		.asPaddingValues()

	BoxWithConstraints(Modifier.fillMaxSize()) {
		val area = with(density) {
			IntSize(
				(maxWidth - padding.calculateLeftPadding(LayoutDirection.Ltr) - padding.calculateRightPadding(LayoutDirection.Ltr))
					.roundToPx().coerceAtLeast(1),
				(maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()).roundToPx().coerceAtLeast(1),
			)
		}
		val cache = remember(document, area, textSize, density, fonts) {
			val type = readingType(textSize)
			val book = TextStyle(
				hyphens = Hyphens.Auto,
				lineBreak = LineBreak.Paragraph,
				localeList = document.language?.let { LocaleList(it) },
			)
			val bodyLine = with(density) { type.body.lineHeight.toPx() }.roundToInt()
			FlowLayoutCache(
				document = document,
				metrics = FlowMetrics(
					width = area.width,
					height = area.height,
					body = type.body.merge(book),
					heading = type.heading.merge(book),
					subheading = type.subheading.merge(book),
					bodyLine = bodyLine,
					blockGap = bodyLine / 2,
					indentStep = with(density) { ReaderSpacing.space6.roundToPx() },
					ruleHeight = bodyLine * 2,
					imageScale = density.density,
				),
				measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr, cacheSize = 0),
				unreadable = unreadable,
			)
		}

		// A new layout reopens at the place last read.
		var request by remember(cache) {
			mutableStateOf(FlowRequest(latest.chapter.coerceIn(0, document.chapterCount - 1), FlowEntry.At(latest.offset)))
		}
		var shown by remember(cache) { mutableStateOf<Pair<FlowRequest, FlowWindow>?>(null) }
		LaunchedEffect(cache, request) {
			val target = request
			shown = target to cache.window(target.chapter)
		}

		shown?.let { (target, window) ->
			key(target, window) {
				val pager = rememberPagerState(initialPage = window.indexOf(target.entry)) { window.items.size }
				val scope = rememberCoroutineScope()

				LaunchedEffect(pager) {
					var previous = -1
					snapshotFlow { pager.settledPage }.collect { index ->
						when {
							window.previous != null && index == 0 ->
								request = FlowRequest(window.chapter - 1, FlowEntry.End)
							window.next != null && index == window.items.lastIndex ->
								request = FlowRequest(window.chapter + 1, FlowEntry.Start)
							else -> {
								val page = window.items[index]
								onPositionChange(Locator.Text(window.chapter, page.start), progress.at(window, page))
							}
						}
						if (previous >= 0 && previous != index) controls = false
						previous = index
					}
				}

				fun turn(forward: Boolean) {
					val next = pager.currentPage + if (forward) 1 else -1
					if (next in window.items.indices) scope.launch { pager.scrollToPage(next) }
				}

				HorizontalPager(
					state = pager,
					modifier = Modifier.fillMaxSize(),
					reverseLayout = rightToLeft,
					beyondViewportPageCount = 1,
					key = { window.items[it].let { page -> page.chapter.toLong() shl 32 or page.start.toLong() } },
				) { index ->
					val page = window.items[index]
					FlowPageView(
						page = page,
						document = document,
						padding = padding,
						areaWidth = area.width,
						onTap = { tap, size ->
							val link = with(density) {
								page.linkAt(
									tap.x - padding.calculateLeftPadding(LayoutDirection.Ltr).toPx(),
									tap.y - padding.calculateTopPadding().toPx(),
									slop = ReaderSpacing.space3.toPx(),
								)
							}
							when {
								controls -> controls = false
								link != null -> request = FlowRequest(
									link.chapter.coerceIn(0, document.chapterCount - 1),
									link.anchor?.let(FlowEntry::Anchor) ?: FlowEntry.Start,
								)
								tap.x < size.width * EDGE -> turn(forward = rightToLeft)
								tap.x > size.width * (1 - EDGE) -> turn(forward = !rightToLeft)
								else -> controls = true
							}
						},
					)
				}
			}
		}

		if (controls && !contents) {
			TopControls(book.title, onBack, Modifier.align(Alignment.TopCenter))
			FlowControls(
				percent = (progress.overall(latest, shown?.second) * 100).roundToInt(),
				rightToLeft = rightToLeft,
				hasContents = document.contents.isNotEmpty(),
				textSize = textSize,
				onJump = { percent ->
					val (chapter, fraction) = progress.locate(percent / 100f)
					request = FlowRequest(chapter, FlowEntry.Fraction(fraction))
				},
				onContents = { contents = true },
				onTextSize = onTextSize,
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}

		if (contents) {
			BackHandler { contents = false }
			Contents(
				document = document,
				current = latest.chapter,
				onPick = { target ->
					contents = false
					controls = false
					request = FlowRequest(
						target.chapter.coerceIn(0, document.chapterCount - 1),
						target.anchor?.let(FlowEntry::Anchor) ?: FlowEntry.Start,
					)
				},
				onClose = { contents = false },
			)
		}
	}
}

@Composable
private fun FlowPageView(
	page: FlowPage,
	document: FlowDocument,
	padding: PaddingValues,
	areaWidth: Int,
	onTap: (Offset, IntSize) -> Unit,
) {
	val colors = ReaderTheme.colors
	val text = remember(page) { AnnotatedString(page.plainText()) }
	Box(
		Modifier
			.fillMaxSize()
			.pointerInput(page) { detectTapGestures { onTap(it, size) } }
			.padding(padding),
	) {
		Canvas(
			Modifier
				.fillMaxSize()
				.semantics { this.text = text },
		) {
			for (piece in page.pieces) {
				when (piece) {
					is FlowPiece.Text -> clipRect(top = piece.top.toFloat(), bottom = (piece.top + piece.height).toFloat()) {
						drawText(
							textLayoutResult = piece.layout,
							color = colors.ink,
							topLeft = Offset(piece.indent.toFloat(), piece.top - piece.layout.getLineTop(piece.firstLine)),
						)
					}
					is FlowPiece.Rule -> {
						val y = piece.top + piece.height / 2f
						drawLine(
							color = colors.rib,
							start = Offset(size.width * RULE_START, y),
							end = Offset(size.width * (1 - RULE_START), y),
							strokeWidth = ReaderSize.rule.toPx(),
						)
					}
					is FlowPiece.Image -> Unit
				}
			}
		}
		for (piece in page.pieces) {
			if (piece is FlowPiece.Image) FlowImage(document, piece, areaWidth)
		}
	}
}

@Composable
private fun FlowImage(document: FlowDocument, piece: FlowPiece.Image, areaWidth: Int) {
	val bitmap by produceState<ImageBitmap?>(null, document, piece.key, piece.width, piece.height) {
		value = try {
			document.image(piece.key, piece.width, piece.height)?.asImageBitmap()
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			// A broken picture leaves a gap; the text around it still reads.
			null
		}
	}
	val density = LocalDensity.current
	Box(
		Modifier
			.offset { IntOffset((areaWidth - piece.width) / 2, piece.top) }
			.size(with(density) { piece.width.toDp() }, with(density) { piece.height.toDp() }),
	) {
		bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
	}
}

@Composable
private fun FlowControls(
	percent: Int,
	rightToLeft: Boolean,
	hasContents: Boolean,
	textSize: ReadingTextSize,
	onJump: (Int) -> Unit,
	onContents: () -> Unit,
	onTextSize: (ReadingTextSize) -> Unit,
	modifier: Modifier = Modifier,
) {
	var scrubbing by remember { mutableStateOf<Int?>(null) }
	val shown = scrubbing ?: percent
	val percentText = stringResource(R.string.reader_percent, shown)
	val sizeLabels = mapOf(
		ReadingTextSize.SMALL to stringResource(R.string.reader_text_small),
		ReadingTextSize.MEDIUM to stringResource(R.string.reader_text_medium),
		ReadingTextSize.LARGE to stringResource(R.string.reader_text_large),
	)
	Column(
		modifier
			.fillMaxWidth()
			.background(ReaderTheme.colors.fogRaised)
			.absorbTaps(),
	) {
		Rule()
		Column(
			Modifier
				.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
				.padding(ReaderSpacing.space4),
			verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space2),
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				FieldLabel(stringResource(R.string.reader_progress), Modifier.weight(1f))
				Text(percentText, style = ReaderTheme.type.label, color = ReaderTheme.colors.ink)
			}
			ReaderScrubber(
				value = shown,
				count = 101,
				onValueChange = { scrubbing = it },
				onValueChangeFinished = {
					scrubbing?.let(onJump)
					scrubbing = null
				},
				stateDescription = percentText,
				reverse = rightToLeft,
			)
			FieldLabel(stringResource(R.string.reader_text_size), Modifier.padding(top = ReaderSpacing.space2))
			ReaderChips(
				options = ReadingTextSize.entries,
				selected = textSize,
				onSelect = onTextSize,
				label = { sizeLabels.getValue(it) },
			)
			if (hasContents) {
				SecondaryButton(
					text = stringResource(R.string.reader_contents),
					onClick = onContents,
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = ReaderSpacing.space2),
				)
			}
		}
	}
}

/** The book's table of contents, nested entries indented. The chapter being read is marked. */
@Composable
private fun Contents(
	document: FlowDocument,
	current: Int,
	onPick: (FlowLink) -> Unit,
	onClose: () -> Unit,
) {
	val here = stringResource(R.string.reader_contents_here)
	// The last entry starting at or before the current chapter is the one being read.
	val currentEntry = document.contents.indexOfLast { it.target.chapter <= current }
	Column(
		Modifier
			.fillMaxSize()
			.background(ReaderTheme.colors.fog)
			.absorbTaps()
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.padding(horizontal = ReaderSpacing.space4),
	) {
		ScreenTitle(stringResource(R.string.reader_contents), onBack = onClose, backLabel = stringResource(R.string.back))
		LazyColumn(Modifier.weight(1f)) {
			itemsIndexed(document.contents) { index, entry ->
				ReaderRow(
					title = entry.title,
					trailing = if (index == currentEntry) here else null,
					onClick = { onPick(entry.target) },
					modifier = Modifier.padding(start = ReaderSpacing.space4 * entry.depth.coerceAtMost(MAX_CONTENTS_DEPTH)),
				)
			}
		}
	}
}

/** Where a [FlowRequest] lands inside its chapter. */
internal sealed interface FlowEntry {
	data object Start : FlowEntry
	data object End : FlowEntry
	data class At(val offset: Int) : FlowEntry
	data class Anchor(val id: String) : FlowEntry
	data class Fraction(val fraction: Float) : FlowEntry
}

/** A move to [chapter]. Compared by identity: asking twice for the same place still moves. */
internal class FlowRequest(val chapter: Int, val entry: FlowEntry)

/** One chapter's pages, plus the pages either side of it so a swipe can carry on into them. */
internal class FlowWindow(
	val chapter: Int,
	val content: FlowContent,
	val pages: List<FlowPage>,
	val previous: FlowPage?,
	val next: FlowPage?,
) {
	val items: List<FlowPage> = listOfNotNull(previous) + pages + listOfNotNull(next)
	private val first = if (previous != null) 1 else 0

	fun indexOf(entry: FlowEntry): Int = first + when (entry) {
		FlowEntry.Start -> 0
		FlowEntry.End -> pages.lastIndex
		is FlowEntry.At -> pages.indexOfOffset(entry.offset)
		is FlowEntry.Anchor -> pages.indexOfOffset(content.anchors[entry.id] ?: 0)
		is FlowEntry.Fraction -> pages.indexOfOffset((content.length * entry.fraction).toInt())
	}
}

/** Chapters laid out for one page and text size. A few are kept for paging back and forth. */
internal class FlowLayoutCache(
	private val document: FlowDocument,
	private val metrics: FlowMetrics,
	private val measurer: TextMeasurer,
	private val unreadable: String,
) {
	private class Chapter(val content: FlowContent, val pages: List<FlowPage>)

	/** The measurer is not thread-safe; one chapter is laid out at a time. */
	private val lock = Mutex()
	private val chapters = object : LinkedHashMap<Int, Chapter>(CACHED_CHAPTERS, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Chapter>?) = size > CACHED_CHAPTERS
	}

	suspend fun window(index: Int): FlowWindow {
		val current = chapter(index)
		val previous = if (index > 0) chapter(index - 1).pages.last() else null
		val next = if (index < document.chapterCount - 1) chapter(index + 1).pages.first() else null
		return FlowWindow(index, current.content, current.pages, previous, next)
	}

	private suspend fun chapter(index: Int): Chapter = lock.withLock {
		chapters[index]?.let { return it }
		val content = try {
			document.chapter(index)
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			// A damaged chapter reads as a note; the rest of the book still opens.
			FlowContent(listOf(FlowBlock.Paragraph(listOf(TextSpan(unreadable)))))
		}
		val pages = withContext(Dispatchers.Default) {
			paginate(index, content, metrics, measurer) { key ->
				try {
					document.imageSize(key)
				} catch (e: CancellationException) {
					throw e
				} catch (_: Exception) {
					null
				}
			}
		}
		Chapter(content, pages).also { chapters[index] = it }
	}

	private companion object {
		const val CACHED_CHAPTERS = 5
	}
}

/** Whole-book progress from the chapter weights, before every chapter has been read. */
internal class FlowProgress(weights: List<Long>) {
	private val before: List<Long> = weights.runningFold(0L) { sum, w -> sum + w }
	private val weights = weights
	private val total = before.last().coerceAtLeast(1L).toFloat()

	/** Progress at the end of [page], so the last page reads 100%. */
	fun at(window: FlowWindow, page: FlowPage): Float = of(window.chapter, page.end / window.content.length.coerceAtLeast(1).toFloat())

	fun startOf(chapter: Int): Float = of(chapter, 0f)

	/** Progress shown on the controls: from the laid-out page when known, else the chapter start. */
	fun overall(position: Locator.Text, window: FlowWindow?): Float {
		val page = window?.takeIf { it.chapter == position.chapter }?.let { it.pages[it.pages.indexOfOffset(position.offset)] }
		return if (page != null) at(window, page) else startOf(position.chapter)
	}

	/** The chapter and the fraction through it at [progress] of the whole book. */
	fun locate(progress: Float): Pair<Int, Float> {
		val target = progress.coerceIn(0f, 1f) * total
		val chapter = (before.indexOfLast { it <= target }).coerceIn(0, weights.lastIndex.coerceAtLeast(0))
		val weight = weights.getOrElse(chapter) { 1L }.coerceAtLeast(1L)
		return chapter to ((target - before[chapter]) / weight).coerceIn(0f, 1f)
	}

	private fun of(chapter: Int, fraction: Float): Float {
		if (chapter !in weights.indices) return 0f
		return ((before[chapter] + weights[chapter] * fraction.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
	}
}

/** The text on [this] page, for screen readers. */
private fun FlowPage.plainText(): String = pieces.filterIsInstance<FlowPiece.Text>().joinToString("\n") { piece ->
	val text = piece.layout.layoutInput.text.text
	text.substring(piece.layout.getLineStart(piece.firstLine), piece.layout.getLineEnd(piece.lastLine))
}

/** Scene breaks are drawn across the middle fifth of the text width. */
private const val RULE_START = 0.4f

private const val MAX_CONTENTS_DEPTH = 3
