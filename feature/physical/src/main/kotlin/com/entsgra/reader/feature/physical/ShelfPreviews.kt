package com.entsgra.reader.feature.physical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.PhysicalBook
import com.entsgra.reader.core.model.ReadingItem
import com.entsgra.reader.core.model.ReadingSession
import com.entsgra.reader.core.model.ReadingStatus

private val sampleBooks = listOf(
	PhysicalBook(1, "The Rings of Saturn", "W. G. Sebald", 296, null, ReadingStatus.READING, 47, 0, 3),
	PhysicalBook(2, "Austerlitz", "W. G. Sebald", 416, null, ReadingStatus.READING, 120, 0, 2),
	PhysicalBook(3, "Vertigo", null, null, null, ReadingStatus.WANT_TO_READ, 0, 0, 1),
	PhysicalBook(4, "The Emigrants", "W. G. Sebald", 237, null, ReadingStatus.FINISHED, 237, 0, 0),
)

private val sampleSittings = listOf(
	ReadingSession(1, ReadingItem.Physical(1), startedAt = 0, endedAt = 1_800_000, startPage = 31, endPage = 47),
	ReadingSession(2, ReadingItem.Physical(1), startedAt = 0, endedAt = 600_000, startPage = 30, endPage = 30),
)

@Composable
private fun Themed(theme: AppTheme, content: @Composable () -> Unit) {
	ReaderTheme(theme) {
		Box(Modifier.background(ReaderTheme.colors.fog)) { content() }
	}
}

@Composable
private fun Shelf(theme: AppTheme) = Themed(theme) {
	ShelfContentView(shelfContent(sampleBooks), onAdd = {}, onOpenBook = {})
}

@Composable
private fun Book(theme: AppTheme) = Themed(theme) {
	PhysicalBookContent(sampleBooks[0], sampleSittings, onBack = {}, onLog = {}, onStatus = {}, onDeleteSitting = {}, onEdit = {}, onDelete = {})
}

@Preview(name = "Shelf light", widthDp = 360, heightDp = 900)
@Composable
private fun ShelfLight() = Shelf(AppTheme.LIGHT)

@Preview(name = "Shelf dark", widthDp = 360, heightDp = 900)
@Composable
private fun ShelfDark() = Shelf(AppTheme.DARK)

@Preview(name = "Shelf light yellow", widthDp = 360, heightDp = 900)
@Composable
private fun ShelfYellow() = Shelf(AppTheme.YELLOW)

@Preview(name = "Shelf light pink", widthDp = 360, heightDp = 900)
@Composable
private fun ShelfPink() = Shelf(AppTheme.PINK)

@Preview(name = "Book light", widthDp = 360, heightDp = 1100)
@Composable
private fun BookLight() = Book(AppTheme.LIGHT)

@Preview(name = "Book dark", widthDp = 360, heightDp = 1100)
@Composable
private fun BookDark() = Book(AppTheme.DARK)

@Preview(name = "Book light yellow", widthDp = 360, heightDp = 1100)
@Composable
private fun BookYellow() = Book(AppTheme.YELLOW)

@Preview(name = "Book light pink", widthDp = 360, heightDp = 1100)
@Composable
private fun BookPink() = Book(AppTheme.PINK)
