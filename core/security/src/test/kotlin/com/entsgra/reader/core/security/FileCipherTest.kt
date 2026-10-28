package com.entsgra.reader.core.security

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileCipherTest {
	@get:Rule val tmp = TemporaryFolder()

	private val crypto = object : CryptoPrimitives {
		init {
			AeadConfig.register()
			StreamingAeadConfig.register()
		}

		override val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
			.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
		override val streamingAead: StreamingAead =
			KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
				.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
	}
	private val cipher = FileCipher(crypto)

	// Spans several 1 MiB segments so seeking crosses segment boundaries.
	private val plain = Random(293).nextBytes(3 * 1024 * 1024 + 517)

	@Test
	fun roundTripsAndIsNotPlaintextOnDisk() {
		val file = File(tmp.root, "book")
		cipher.encrypt(plain.inputStream(), file)

		assertThat(file.readBytes().asList().windowed(16).contains(plain.take(16))).isFalse()
		assertThat(cipher.openInputStream(file).use { it.readBytes() }).isEqualTo(plain)
		assertThat(File(tmp.root, "book.part").exists()).isFalse()
	}

	@Test
	fun seeksAcrossSegments() {
		val file = File(tmp.root, "book")
		cipher.encrypt(plain.inputStream(), file)

		val at = 2 * 1024 * 1024 - 10
		val buffer = ByteBuffer.allocate(64)
		cipher.openChannel(file).use { channel ->
			assertThat(channel.size()).isEqualTo(plain.size.toLong())
			channel.position(at.toLong())
			while (buffer.hasRemaining()) channel.read(buffer)
		}
		assertThat(buffer.array()).isEqualTo(plain.copyOfRange(at, at + 64))
	}

	@Test
	fun renamedFileFailsToDecrypt() {
		val file = File(tmp.root, "book")
		cipher.encrypt(plain.inputStream(), file)
		val moved = File(tmp.root, "other").also { file.renameTo(it) }

		assertThrows(Exception::class.java) { cipher.openInputStream(moved).use { it.readBytes() } }
	}

	@Test
	fun databaseKeyIsStableAndWrapped() {
		val keyFile = File(tmp.root, "db.key")
		val first = DatabaseKey(keyFile, crypto).passphrase()
		val second = DatabaseKey(keyFile, crypto).passphrase()

		assertThat(first).hasLength(32)
		assertThat(second).isEqualTo(first)
		assertThat(keyFile.readBytes().asList().windowed(32).contains(first.asList())).isFalse()
		assertThrows(GeneralSecurityException::class.java) {
			crypto.aead.decrypt(keyFile.readBytes(), "wrong".toByteArray())
		}
	}
}
