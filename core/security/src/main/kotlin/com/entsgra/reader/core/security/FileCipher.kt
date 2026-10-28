package com.entsgra.reader.core.security

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts book files at rest with Tink StreamingAead (AES256_GCM_HKDF_1MB).
 *
 * The file name is bound as associated data, so a ciphertext renamed or swapped for another
 * book fails to decrypt. All methods block; call from a background dispatcher.
 */
@Singleton
class FileCipher @Inject constructor(private val crypto: CryptoPrimitives) {

	/** Streams [source] into [target], encrypted. Writes a temp file first, then renames. */
	fun encrypt(source: InputStream, target: File) {
		val temp = File(target.parentFile, "${target.name}.part")
		try {
			writeDurably(temp) { raw ->
				crypto.streamingAead.newEncryptingStream(raw, associatedData(target)).use { out ->
					source.copyTo(out, BUFFER_BYTES)
				}
			}
			check(temp.renameTo(target)) { "Could not move encrypted file into place" }
		} finally {
			temp.delete()
		}
	}

	/** Sequential plaintext stream, for import scans and text formats. */
	fun openInputStream(file: File): InputStream =
		crypto.streamingAead.newDecryptingStream(FileInputStream(file), associatedData(file))

	/** Random-access plaintext channel, for archives and paged formats. */
	fun openChannel(file: File): SeekableByteChannel {
		val channel = crypto.streamingAead.newSeekableDecryptingChannel(
			FileInputStream(file).channel,
			associatedData(file),
		)
		// Tink picks the matching key on first read and throws from size() until then.
		// Zip readers ask for size() first, so prime it here.
		try {
			channel.read(ByteBuffer.allocate(1))
			channel.position(0)
		} catch (e: Exception) {
			channel.close()
			throw e
		}
		return channel
	}

	private fun associatedData(file: File) = "293-reader/file/v1/${file.name}".toByteArray()

	private companion object {
		const val BUFFER_BYTES = 64 * 1024
	}
}
