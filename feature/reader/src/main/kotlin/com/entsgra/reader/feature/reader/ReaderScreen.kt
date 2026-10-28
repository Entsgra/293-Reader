package com.entsgra.reader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.component.ErrorNotice
import com.entsgra.reader.core.designsystem.component.ScreenTitle
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.reader.api.BookOpenException

@Composable
internal fun ReaderScreen(
	onBack: () -> Unit,
	viewModel: ReaderViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val book by viewModel.book.collectAsStateWithLifecycle()
	val page by viewModel.page.collectAsStateWithLifecycle()

	LifecycleStartEffect(viewModel) {
		viewModel.onStart()
		onStopOrDispose { viewModel.onStop() }
	}
	LaunchedEffect(state) { if (state == ReaderUiState.Gone) onBack() }

	Box(
		Modifier
			.fillMaxSize()
			.background(ReaderTheme.colors.fog),
	) {
		when (val current = state) {
			ReaderUiState.Loading, ReaderUiState.Gone -> Unit
			is ReaderUiState.Failed -> ReaderError(current.reason, onBack)
			is ReaderUiState.Paged -> book?.let {
				PagedReader(
					document = current.document,
					book = it,
					page = page,
					pool = viewModel.bitmapPool,
					onPageChange = viewModel::onPageChange,
					onReadingDirection = viewModel::setReadingDirection,
					onSpreadMode = viewModel::setSpreadMode,
					onBack = onBack,
				)
			}
		}
	}
}

@Composable
private fun ReaderError(reason: BookOpenException.Reason, onBack: () -> Unit) {
	val message = when (reason) {
		BookOpenException.Reason.PROTECTED -> R.string.reader_error_protected
		BookOpenException.Reason.DAMAGED -> R.string.reader_error_damaged
		BookOpenException.Reason.EMPTY -> R.string.reader_error_empty
		BookOpenException.Reason.UNSUPPORTED -> R.string.reader_error_unsupported
		BookOpenException.Reason.TOO_LARGE -> R.string.reader_error_too_large
	}
	Column(
		Modifier
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.padding(horizontal = ReaderSpacing.space4),
	) {
		ScreenTitle(stringResource(R.string.reader_error_title), onBack = onBack, backLabel = stringResource(R.string.back))
		ErrorNotice(stringResource(message))
	}
}

/**
 * Hides the status and navigation bars while [immersive]; a swipe from the edge shows them for
 * a moment. They come back when the reader closes.
 */
@Composable
internal fun ImmersiveEffect(immersive: Boolean) {
	val view = LocalView.current
	DisposableEffect(view, immersive) {
		val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
		val controller = WindowCompat.getInsetsController(window, view)
		controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		if (immersive) {
			controller.hide(WindowInsetsCompat.Type.systemBars())
		} else {
			controller.show(WindowInsetsCompat.Type.systemBars())
		}
		onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
	}
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivity()
	else -> null
}
