package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.entsgra.reader.core.designsystem.theme.ReaderSize
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing

/**
 * A dialog is a terrace: a `fog-raised` face on its ledge, square, no shadow. A headline,
 * then [content]; buttons go last, primary first.
 */
@Composable
fun ReaderDialog(
	title: String,
	onDismiss: () -> Unit,
	content: @Composable ColumnScope.() -> Unit,
) {
	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
	) {
		Terrace(
			modifier = Modifier
				.imePadding()
				.padding(horizontal = ReaderSpacing.space4)
				.widthIn(max = ReaderSize.dialogMaxWidth)
				.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier.padding(ReaderSpacing.space4),
				verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space4),
			) {
				SectionHeadline(title)
				content()
			}
		}
	}
}

/** Dialog buttons, stacked full width: primary first, then the rest. */
@Composable
fun DialogButtons(content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = ReaderSpacing.space2),
		verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space2),
		content = content,
	)
}
