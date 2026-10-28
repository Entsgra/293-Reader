package com.entsgra.reader.reader.pdf

import android.graphics.pdf.PdfRenderer
import com.entsgra.reader.core.model.BookFormatId
import com.entsgra.reader.reader.api.BookDocument
import com.entsgra.reader.reader.api.BookFormat
import com.entsgra.reader.reader.api.BookOpenException
import com.entsgra.reader.reader.api.BookSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF through the platform [PdfRenderer]. The renderer needs a seekable file descriptor, so the
 * decrypted book is copied into a memfd: anonymous memory, never a file on disk.
 */
class PdfFormat @Inject constructor() : BookFormat {
	override val id = BookFormatId.PDF

	override fun canOpen(header: ByteArray, fileName: String): Boolean =
		String(header, Charsets.ISO_8859_1).contains("%PDF-")

	override suspend fun open(source: BookSource): BookDocument = withContext(Dispatchers.IO) {
		val fd = try {
			source.openMemoryFd()
		} catch (e: IOException) {
			throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
		}
		try {
			PdfPagedDocument(PdfRenderer(fd))
		} catch (e: SecurityException) {
			fd.close()
			throw BookOpenException(BookOpenException.Reason.PROTECTED, e)
		} catch (e: IOException) {
			fd.close()
			throw BookOpenException(BookOpenException.Reason.DAMAGED, e)
		}
	}
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PdfModule {
	@Binds
	@IntoSet
	fun bindPdf(format: PdfFormat): BookFormat
}
