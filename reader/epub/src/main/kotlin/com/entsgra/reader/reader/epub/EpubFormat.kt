package com.entsgra.reader.reader.epub

import com.entsgra.reader.core.model.BookFormatId
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
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** EPUB 2 and 3, DRM-free. Read with the app's own parser over the decrypting zip channel. */
class EpubFormat @Inject constructor() : BookFormat {
	override val id = BookFormatId.EPUB

	override fun canOpen(header: ByteArray, fileName: String): Boolean =
		header.startsWith(ZIP) &&
			(header.startsWith(MIMETYPE, offset = 30) || fileName.substringAfterLast('.').lowercase() == "epub")

	override suspend fun open(source: BookSource): BookDocument = withContext(Dispatchers.IO) {
		val channel = try {
			source.openChannel()
		} catch (e: IOException) {
			throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
		}
		EpubDocument.open(Archive.zip(channel))
	}
}

private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0) =
	size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** OCF puts an uncompressed `mimetype` entry first, so its name and content sit at byte 30. */
private val MIMETYPE = "mimetypeapplication/epub+zip".toByteArray(Charsets.US_ASCII)

@Module
@InstallIn(SingletonComponent::class)
internal interface EpubModule {
	@Binds
	@IntoSet
	fun bindEpub(format: EpubFormat): BookFormat
}
