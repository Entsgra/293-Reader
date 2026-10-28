package com.entsgra.reader.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.entsgra.reader.core.designsystem.icon.ReaderIcons
import com.entsgra.reader.core.designsystem.theme.ReaderSpacing
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.AppTheme

@Composable
private fun Gallery(theme: AppTheme) {
	ReaderTheme(theme) {
		var on by remember { mutableStateOf(true) }
		var chip by remember { mutableStateOf(theme) }
		var page by remember { mutableIntStateOf(46) }
		Column(
			modifier = Modifier
				.background(ReaderTheme.colors.fog)
				.padding(horizontal = ReaderSpacing.space4),
			verticalArrangement = Arrangement.spacedBy(ReaderSpacing.space4),
		) {
			ScreenTitle("Library")
			SweepKeyCard {
				FieldLabel("Now reading")
				BodyText("The Rings of Saturn")
				WindowPlate(label = "Page", figure = "47", plateLabel = "Of", plateValue = "312")
				InlineControl(label = "Right to left", checked = on, onCheckedChange = { on = it })
			}
			ReaderChips(
				options = AppTheme.entries,
				selected = chip,
				onSelect = { chip = it },
				label = { it.name },
			)
			ReaderScrubber(
				value = page,
				count = 312,
				onValueChange = { page = it },
				onValueChangeFinished = {},
				stateDescription = "Page ${page + 1} of 312",
			)
			ReaderTextField(value = "", onValueChange = {}, label = "Search", placeholder = "Title or author", leadingIcon = ReaderIcons.Search)
			ReaderTextField(value = "Comics", onValueChange = {}, label = "Name", error = "A folder with that name is already here.")
			Row(horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space3)) {
				CoverTile(title = "The Rings of Saturn", formatLabel = "PDF", image = null, onClick = {}, modifier = Modifier.weight(1f))
				CoverTile(title = "Vertigo", formatLabel = "EPUB", image = null, onClick = {}, modifier = Modifier.weight(1f))
				CoverTilePlaceholder(Modifier.weight(1f))
			}
			ReaderRow(title = "Austerlitz", trailing = "47%", leadingIcon = ReaderIcons.Folder, onClick = {})
			ReaderRow(title = "Vertigo", trailing = "Done", onClick = {})
			LiveStatus("Reading now")
			ErrorNotice("That file is not a book. Pick a PDF, EPUB, CBZ, CB7 or TXT file.")
			Row(horizontalArrangement = Arrangement.spacedBy(ReaderSpacing.space3)) {
				SecondaryButton("Cancel", onClick = {})
				DestructiveButton("Delete", onClick = {})
			}
			PrimaryButton("Import", onClick = {}, modifier = Modifier.fillMaxWidth())
			PrimaryButton("Import", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
			ReaderBottomBar {
				ReaderBottomBarItem(selected = true, onClick = {}, icon = ReaderIcons.Library, label = "Library")
				ReaderBottomBarItem(selected = false, onClick = {}, icon = ReaderIcons.Shelf, label = "Shelf")
				ReaderBottomBarItem(selected = false, onClick = {}, icon = ReaderIcons.Log, label = "Log")
				ReaderBottomBarItem(selected = false, onClick = {}, icon = ReaderIcons.Settings, label = "Settings")
			}
		}
	}
}

@Preview(name = "Light", widthDp = 360, heightDp = 1960)
@Composable
private fun GalleryLight() = Gallery(AppTheme.LIGHT)

@Preview(name = "Dark", widthDp = 360, heightDp = 1960)
@Composable
private fun GalleryDark() = Gallery(AppTheme.DARK)

@Preview(name = "Light yellow", widthDp = 360, heightDp = 1960)
@Composable
private fun GalleryYellow() = Gallery(AppTheme.YELLOW)

@Preview(name = "Light pink", widthDp = 360, heightDp = 1960)
@Composable
private fun GalleryPink() = Gallery(AppTheme.PINK)
