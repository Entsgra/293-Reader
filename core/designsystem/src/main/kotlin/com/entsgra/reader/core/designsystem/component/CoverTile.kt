package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme

/**
 * A book in the grid: its cover on a terrace. Without a cover image the face is typographic:
 * the title in `title`, the format in `label-small` at the foot. Long press for actions.
 */
@Composable
fun CoverTile(
	title: String,
	formatLabel: String,
	image: ImageBitmap?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
	onLongClickLabel: String? = null,
) {
	Terrace(
		modifier = modifier.semantics(mergeDescendants = true) { contentDescription = title },
		onClick = onClick,
		onLongClick = onLongClick,
		onLongClickLabel = onLongClickLabel,
	) {
		Box(Modifier.aspectRatio(ReaderSize.COVER_ASPECT)) {
			if (image != null) {
				Image(
					bitmap = image,
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Column(
					Modifier
						.fillMaxSize()
						.padding(ReaderSpacing.space2)
						.clearAndSetSemantics {},
				) {
					// The title takes what the label leaves and ellipsizes there; the label sits at the foot.
					Text(
						text = title,
						style = ReaderTheme.type.title,
						color = LocalContentColor.current,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f),
					)
					Text(
						text = formatLabel.uppercase(),
						style = ReaderTheme.type.labelSmall,
						color = ReaderTheme.colors.inkMuted,
						maxLines = 1,
					)
				}
			}
		}
	}
}

/** Where a cover will be while its page loads: the face without content or ledge press. */
@Composable
fun CoverTilePlaceholder(modifier: Modifier = Modifier) {
	Terrace(modifier = modifier) {
		Box(Modifier.aspectRatio(ReaderSize.COVER_ASPECT))
	}
}
