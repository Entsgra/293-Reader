package com.entsgra.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.entsgra.reader.core.designsystem.component.FieldLabel
import com.entsgra.reader.core.designsystem.component.ReaderChips
import com.entsgra.reader.core.designsystem.component.ReaderIconButton
import com.entsgra.reader.core.designsystem.component.ReaderScrubber
import com.entsgra.reader.core.designsystem.component.Rule
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.Book
import com.entsgra.reader.core.model.ReadingDirection
import com.entsgra.reader.core.model.SpreadMode
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.PagedDocument
import kotlinx.coroutines.launch

/**
 * Page mode for PDF and comics. Swipe, or tap the outer thirds, to turn; tap the middle for the
 * controls. Taps jump straight to the page: nothing slides on its own. Two-page spreads follow
 * the book's setting, with Auto showing two pages in landscape.
 */
@Composable
internal fun PagedReader(
	document: PagedDocument,
	book: Book,
	page: Int,
	pool: BitmapPool,
	onPageChange: (IntRange) -> Unit,
	onReadingDirection: (ReadingDirection) -> Unit,
	onSpreadMode: (SpreadMode) -> Unit,
	onBack: () -> Unit,
) {
	var controls by rememberSaveable { mutableStateOf(true) }
	ImmersiveEffect(immersive = !controls)
	val rightToLeft = book.readingDirection == ReadingDirection.RTL
	val currentPage by rememberUpdatedState(page)

	BoxWithConstraints(Modifier.fillMaxSize()) {
		val double = document.pageCount > 1 && when (book.spreadMode) {
			SpreadMode.SINGLE -> false
			SpreadMode.DOUBLE -> true
			SpreadMode.AUTO -> maxWidth > maxHeight
		}
		val spreads = remember(document.pageCount, double) { spreadsOf(document.pageCount, double) }

		// A new spread layout starts a new pager on the same page.
		key(spreads) {
			val pager = rememberPagerState(initialPage = spreads.indexOfPage(currentPage)) { spreads.size }
			val scope = rememberCoroutineScope()
			val zoom = remember { ZoomState() }
			val idle = remember { ZoomState() }

			LaunchedEffect(pager) {
				var previous = -1
				snapshotFlow { pager.settledPage }.collect { index ->
					onPageChange(spreads[index])
					if (previous >= 0 && previous != index) {
						zoom.reset()
						controls = false
					}
					previous = index
				}
			}

			fun turn(forward: Boolean) {
				val target = pager.currentPage + if (forward) 1 else -1
				if (target in spreads.indices) scope.launch { pager.scrollToPage(target) }
			}

			HorizontalPager(
				state = pager,
				modifier = Modifier.fillMaxSize(),
				reverseLayout = rightToLeft,
				beyondViewportPageCount = 1,
				userScrollEnabled = !zoom.isZoomed,
				key = { spreads[it].first },
			) { index ->
				SpreadView(
					document = document,
					pages = spreads[index],
					rightToLeft = rightToLeft,
					pool = pool,
					zoom = if (index == pager.currentPage) zoom else idle,
					onTap = { x, width ->
						when {
							controls -> controls = false
							x < width * EDGE -> turn(forward = rightToLeft)
							x > width * (1 - EDGE) -> turn(forward = !rightToLeft)
							else -> controls = true
						}
					},
				)
			}

			if (controls) {
				TopControls(book.title, onBack, Modifier.align(Alignment.TopCenter))
				BottomControls(
					document = document,
					book = book,
					pager = pager,
					spreads = spreads,
					onJump = { target -> scope.launch { pager.scrollToPage(spreads.indexOfPage(target)) } },
					onReadingDirection = onReadingDirection,
					onSpreadMode = onSpreadMode,
					modifier = Modifier.align(Alignment.BottomCenter),
				)
			}
		}
	}
}

@Composable
internal fun TopControls(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
	Column(
		modifier
			.fillMaxWidth()
			.background(ReaderTheme.colors.fogRaised)
			.absorbTaps()
			.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
	) {
		Row(
			Modifier
				.heightIn(min = ReaderSize.barHeight)
				.padding(horizontal = ReaderSpacing.space2),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space2),
		) {
			ReaderIconButton(ReaderIcons.Back, stringResource(R.string.back), onBack)
			Text(
				text = title,
				style = ReaderTheme.type.title,
				color = ReaderTheme.colors.ink,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
		}
		Rule()
	}
}

@Composable
private fun BottomControls(
	document: PagedDocument,
	book: Book,
	pager: PagerState,
	spreads: List<IntRange>,
	onJump: (Int) -> Unit,
	onReadingDirection: (ReadingDirection) -> Unit,
	onSpreadMode: (SpreadMode) -> Unit,
	modifier: Modifier = Modifier,
) {
	val shown = spreads[pager.currentPage.coerceIn(spreads.indices)]
	var scrubbing by remember { mutableStateOf<Int?>(null) }
	val count = document.pageCount
	val pageText = when (val target = scrubbing) {
		null -> if (shown.first == shown.last) {
			stringResource(R.string.reader_page_of, shown.first + 1, count)
		} else {
			stringResource(R.string.reader_pages_of, shown.first + 1, shown.last + 1, count)
		}
		else -> stringResource(R.string.reader_page_of, target + 1, count)
	}
	val directionLabels = mapOf(
		ReadingDirection.LTR to stringResource(R.string.reader_ltr),
		ReadingDirection.RTL to stringResource(R.string.reader_rtl),
	)
	val spreadLabels = mapOf(
		SpreadMode.SINGLE to stringResource(R.string.reader_spread_single),
		SpreadMode.DOUBLE to stringResource(R.string.reader_spread_double),
		SpreadMode.AUTO to stringResource(R.string.reader_spread_auto),
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
				FieldLabel(stringResource(R.string.reader_page), Modifier.weight(1f))
				Text(pageText.uppercase(), style = ReaderTheme.type.label, color = ReaderTheme.colors.ink)
			}
			ReaderScrubber(
				value = scrubbing ?: shown.first,
				count = count,
				onValueChange = { scrubbing = it },
				onValueChangeFinished = {
					scrubbing?.let(onJump)
					scrubbing = null
				},
				stateDescription = pageText,
				reverse = book.readingDirection == ReadingDirection.RTL,
			)
			FieldLabel(stringResource(R.string.reader_direction), Modifier.padding(top = ReaderSpacing.space2))
			ReaderChips(
				options = ReadingDirection.entries,
				selected = book.readingDirection,
				onSelect = onReadingDirection,
				label = { directionLabels.getValue(it) },
			)
			FieldLabel(stringResource(R.string.reader_spread), Modifier.padding(top = ReaderSpacing.space2))
			ReaderChips(
				options = SpreadMode.entries,
				selected = book.spreadMode,
				onSelect = onSpreadMode,
				label = { spreadLabels.getValue(it) },
			)
		}
	}
}

/** Keeps taps on the controls from reaching the page underneath. */
internal fun Modifier.absorbTaps() = pointerInput(Unit) { detectTapGestures {} }

/** Share of the width at each side that turns the page on tap. */
internal const val EDGE = 1f / 3f
