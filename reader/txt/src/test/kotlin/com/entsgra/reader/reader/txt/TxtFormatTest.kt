package com.entsgra.reader.reader.txt

import android.os.ParcelFileDescriptor
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.BookSource
import com.entsgra.reader.reader.api.FlowDocument
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TxtFormatTest {
	private val format = TxtFormat()

	private class Source(private val bytes: ByteArray, override val size: Long = bytes.size.toLong()) : BookSource {
		override fun openStream(): InputStream = ByteArrayInputStream(bytes)
		override fun openChannel(): SeekableByteChannel = throw UnsupportedOperationException()
		override fun openMemoryFd(): ParcelFileDescriptor = throw UnsupportedOperationException()
	}

	@Test
	fun onlyTxtFilesAreClaimed() {
		assertThat(format.canOpen(ByteArray(0), "Notes.TXT")).isTrue()
		assertThat(format.canOpen(ByteArray(0), "notes.md")).isFalse()
	}

	@Test
	fun opensWithChaptersContentsAndWeights() = runTest {
		val filler = "Words enough to make this a chapter and not a contents line. ".repeat(5)
		val text = "Title: Dubliners\nAuthor: James Joyce\n\nChapter I\n\n$filler\n\nChapter II\n\n$filler"
		val doc = format.open(Source(text.toByteArray())) as FlowDocument
		assertThat(doc.metadata.title).isEqualTo("Dubliners")
		assertThat(doc.metadata.author).isEqualTo("James Joyce")
		assertThat(doc.chapterCount).isEqualTo(3)
		assertThat(doc.contents.map { it.title to it.target.chapter })
			.containsExactly("Chapter I" to 1, "Chapter II" to 2).inOrder()
		assertThat(doc.chapterWeights).hasSize(3)
		assertThat(doc.chapter(1).length).isEqualTo(doc.chapterWeights[1].toInt())
		assertThat(doc.cover(100, 100)).isNull()
	}

	@Test
	fun emptyFileIsEmpty() = runTest {
		val e = runCatching { format.open(Source("\n\n".toByteArray())) }.exceptionOrNull()
		assertThat((e as BookOpenException).reason).isEqualTo(BookOpenException.Reason.EMPTY)
	}

	@Test
	fun hugeFileIsRefusedBeforeReading() = runTest {
		val e = runCatching { format.open(Source(ByteArray(0), size = 1L shl 40)) }.exceptionOrNull()
		assertThat((e as BookOpenException).reason).isEqualTo(BookOpenException.Reason.TOO_LARGE)
	}

	@Test
	fun unreadableFileIsDamaged() = runTest {
		val broken = object : BookSource {
			override val size = 10L
			override fun openStream(): InputStream = throw IOException("auth tag mismatch")
			override fun openChannel(): SeekableByteChannel = throw UnsupportedOperationException()
			override fun openMemoryFd(): ParcelFileDescriptor = throw UnsupportedOperationException()
		}
		val e = runCatching { format.open(broken) }.exceptionOrNull()
		assertThat((e as BookOpenException).reason).isEqualTo(BookOpenException.Reason.DAMAGED)
	}
}
