package com.entsgra.reader.reader.comic

import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookFormat
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.BookSource
import com.entsgra.reader.reader.archive.Archive
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** CBZ: a zip of page images, read in place through the decrypting channel. */
class CbzFormat @Inject constructor(private val pool: BitmapPool) : BookFormat {
	override val id = BookFormatId.CBZ

	override fun canOpen(header: ByteArray, fileName: String): Boolean =
		header.startsWith(ZIP) && fileName.substringAfterLast('.').lowercase() in setOf("cbz", "zip")

	override suspend fun open(source: BookSource): BookDocument =
		openComic(source, pool, Archive::zip)
}

/** CB7: a 7z of page images. Solid archives decode front to back; see [ComicDocument]. */
class Cb7Format @Inject constructor(private val pool: BitmapPool) : BookFormat {
	override val id = BookFormatId.CB7

	override fun canOpen(header: ByteArray, fileName: String): Boolean = header.startsWith(SEVEN_ZIP)

	override suspend fun open(source: BookSource): BookDocument =
		openComic(source, pool, Archive::sevenZip)
}

private suspend fun openComic(
	source: BookSource,
	pool: BitmapPool,
	archiveOf: (SeekableByteChannel) -> Archive,
): BookDocument = withContext(Dispatchers.IO) {
	val channel = try {
		source.openChannel()
	} catch (e: IOException) {
		throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
	}
	ComicDocument.open(archiveOf(channel), pool)
}

private fun ByteArray.startsWith(prefix: ByteArray) =
	size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val SEVEN_ZIP = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

@Module
@InstallIn(SingletonComponent::class)
internal interface ComicModule {
	@Binds
	@IntoSet
	fun bindCbz(format: CbzFormat): BookFormat

	@Binds
	@IntoSet
	fun bindCb7(format: Cb7Format): BookFormat
}
