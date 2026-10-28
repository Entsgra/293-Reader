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
	private val cipher = FileCipher(crypto, Keyring())

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
		val appLock = AppLockStore(File(tmp.root, "app.lock"))
		val first = DatabaseKey(keyFile, crypto, appLock).passphrase()
		val second = DatabaseKey(keyFile, crypto, appLock).passphrase()

		assertThat(first).hasLength(32)
		assertThat(second).isEqualTo(first)
		assertThat(keyFile.readBytes().asList().windowed(32).contains(first.asList())).isFalse()
		assertThrows(GeneralSecurityException::class.java) {
			crypto.aead.decrypt(keyFile.readBytes(), "wrong".toByteArray())
		}
	}

	@Test
	fun closedAppLockHoldsBackTheDatabaseKey() {
		val keyFile = File(tmp.root, "db.key")
		val appLock = AppLockStore(File(tmp.root, "app.lock"))
		val key = DatabaseKey(keyFile, crypto, appLock)
		val passphrase = key.passphrase()
		appLock.write(AppLockRecord(pin = true, box = SecretBox(TEST_PARAMS, ByteArray(16), ByteArray(40)), biometric = null))
		key.deleteStored()

		assertThat(appLock.isEnabled).isTrue()
		assertThrows(AppLockedException::class.java) { key.passphrase() }

		val keyset = com.google.crypto.tink.TinkProtoKeysetFormat.serializeKeyset(
			KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB")),
			com.google.crypto.tink.InsecureSecretKeyAccess.get(),
		)
		appLock.open(AppSecrets.encode(passphrase, keyset))
		assertThat(key.passphrase()).isEqualTo(passphrase)
		assertThat(keyFile.exists()).isFalse()
	}

	@Test
	fun folderFilesNeedTheFolderKey() {
		val keyring = Keyring()
		val cipher = FileCipher(crypto, keyring)
		val file = File(tmp.root, "locked")
		assertThrows(FolderLockedException::class.java) { cipher.encrypt(plain.inputStream(), file, keyFolderId = 4) }

		keyring.put(4, FolderKeys.load(FolderKeys.generate()))
		cipher.encrypt(plain.inputStream(), file, keyFolderId = 4)
		assertThat(cipher.openInputStream(file, keyFolderId = 4).use { it.readBytes() }).isEqualTo(plain)
		// The app key cannot read it, and once the folder locks neither can anything else.
		assertThrows(Exception::class.java) { cipher.openInputStream(file).use { it.readBytes() } }
		keyring.lock(4)
		assertThrows(FolderLockedException::class.java) { cipher.openChannel(file, keyFolderId = 4) }
	}
}
