package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.data.ApplicationScope
import com.entsgra.reader.core.data.library.CoverStore
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.core.database.entity.FolderKeyEntity
import com.entsgra.reader.core.model.SecretKind
import com.entsgra.reader.core.model.asFolderLock
import com.entsgra.reader.core.security.BiometricInvalidatedException
import com.entsgra.reader.core.security.FolderVault
import com.entsgra.reader.core.security.KdfParams
import com.entsgra.reader.core.security.Keyring
import com.entsgra.reader.core.security.SecretBox
import com.entsgra.reader.core.security.SecretResult
import com.entsgra.reader.reader.api.BitmapPool
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How biometric unlock can start for a folder. */
sealed interface BiometricStart {
	data class Ready(val cipher: Cipher) : BiometricStart

	/** Not set up for this folder. */
	data object None : BiometricStart

	/** New biometrics were enrolled; the wrap is gone. Unlock with the secret, then set it up again. */
	data object Invalidated : BiometricStart
}

/**
 * Locking, unlocking and unlocking folders for good. Keys live in the [Keyring] while a folder
 * is unlocked; this class stores their sealed form and keeps books under the right key through
 * the [Rekeyer].
 */
@Singleton
class FolderLocks @Inject constructor(
	private val database: DatabaseProvider,
	private val vault: FolderVault,
	private val keyring: Keyring,
	private val rekeyer: Rekeyer,
	private val covers: CoverStore,
	private val bitmapPool: BitmapPool,
	@ApplicationScope private val scope: CoroutineScope,
) {
	/** Folders whose keys are open right now. */
	val unlocked: StateFlow<Set<Long>> = keyring.unlocked

	/** Re-encryption under way, for a status line. */
	val progress: StateFlow<Rekeying?> = rekeyer.progress

	/** Folders that have a key: locked unless in [unlocked]. */
	val locked: Flow<Set<Long>> = flow {
		emitAll(database.get().folderKeyDao().observeAll().map { rows -> rows.map { it.folderId }.toSet() })
	}

	suspend fun lockCheck(folderId: Long): LockCheck {
		val folders = database.get().folderDao().all().associateBy { it.id }
		return lockCheck(folderId, folders)
	}

	/**
	 * Sets a lock: new keys under [secret], then the folder's books and subfolder names are
	 * re-encrypted under them in the background. Zeroes [secret].
	 */
	suspend fun lock(folderId: Long, kind: SecretKind, secret: CharArray): LockCheck {
		val db = database.get()
		val check = lockCheck(folderId, db.folderDao().all().associateBy { it.id })
		if (check != LockCheck.OK) {
			secret.fill('\u0000')
			return check
		}
		val box = withContext(Dispatchers.Default) { vault.create(folderId, secret) }
		db.folderKeyDao().lock(box.asEntity(folderId), kind.asFolderLock())
		settle()
		return LockCheck.OK
	}

	/** Zeroes [secret]. Starts finishing any re-encryption the folder still owes. */
	suspend fun unlock(folderId: Long, secret: CharArray): UnlockResult {
		val key = database.get().folderKeyDao().get(folderId) ?: return UnlockResult.Ok
		val result = withContext(Dispatchers.Default) { vault.unlock(folderId, key.box(), secret) }
		if (result is SecretResult.Ok) settle()
		return result.asUnlockResult()
	}

	suspend fun waitMs(folderId: Long): Long = withContext(Dispatchers.IO) { vault.waitMs(folderId) }

	suspend fun hasBiometric(folderId: Long): Boolean = database.get().folderKeyDao().get(folderId)?.biometric != null

	suspend fun biometricStart(folderId: Long): BiometricStart {
		val sealed = database.get().folderKeyDao().get(folderId)?.biometric ?: return BiometricStart.None
		return try {
			BiometricStart.Ready(withContext(Dispatchers.IO) { vault.biometricUnlockCipher(folderId, sealed) })
		} catch (_: BiometricInvalidatedException) {
			removeBiometric(folderId)
			BiometricStart.Invalidated
		}
	}

	/** After the prompt matched with [cipher] from [biometricStart]. */
	suspend fun biometricUnlock(folderId: Long, cipher: Cipher) {
		val sealed = database.get().folderKeyDao().get(folderId)?.biometric ?: return
		withContext(Dispatchers.IO) { vault.biometricUnlock(folderId, cipher, sealed) }
		settle()
	}

	/** A cipher for the enrollment prompt. The folder must be unlocked. */
	suspend fun biometricEnrollCipher(folderId: Long): Cipher =
		withContext(Dispatchers.IO) { vault.biometricEnrollCipher(folderId) }

	suspend fun biometricEnroll(folderId: Long, cipher: Cipher) {
		val sealed = withContext(Dispatchers.IO) { vault.biometricEnroll(folderId, cipher) }
		database.get().folderKeyDao().setBiometric(folderId, sealed)
	}

	suspend fun removeBiometric(folderId: Long) {
		database.get().folderKeyDao().setBiometric(folderId, null)
		withContext(Dispatchers.IO) { vault.removeBiometric(folderId) }
	}

	/** New secret, same keys: nothing is re-encrypted. Zeroes both secrets. */
	suspend fun changeSecret(folderId: Long, current: CharArray, kind: SecretKind, new: CharArray): UnlockResult {
		val dao = database.get().folderKeyDao()
		val key = dao.get(folderId) ?: return UnlockResult.Ok
		val result = withContext(Dispatchers.Default) { vault.rewrap(folderId, key.box(), current, new) }
		if (result is SecretResult.Ok) {
			val box = result.value
			dao.rewrap(folderId, box.params.memoryKib, box.params.iterations, box.params.parallelism, box.salt, box.sealed)
			dao.setKind(folderId, kind.asFolderLock())
		}
		return result.asUnlockResult()
	}

	/**
	 * Takes the lock off once [secret] checks out: books and names go back to the app key in the
	 * background, and the key is deleted after the last one. Zeroes [secret].
	 */
	suspend fun removeLock(folderId: Long, secret: CharArray): UnlockResult {
		val dao = database.get().folderKeyDao()
		val key = dao.get(folderId) ?: return UnlockResult.Ok
		val result = withContext(Dispatchers.Default) { vault.unlock(folderId, key.box(), secret) }
		if (result is SecretResult.Ok) {
			dao.markRemoving(folderId)
			settle()
		}
		return result.asUnlockResult()
	}

	/** Locks one folder now. */
	fun lock(folderId: Long) {
		keyring.lock(folderId)
		dropPlaintextCaches()
	}

	/** Locks every folder: screen off, the timeout, or by hand. */
	fun lockAll() {
		keyring.lockAll()
		dropPlaintextCaches()
	}

	/** Re-encrypts whatever the open keys allow, in the application scope. */
	fun settle() {
		scope.launch { rekeyer.settle() }
	}

	private fun dropPlaintextCaches() {
		covers.clearMemory()
		bitmapPool.clear()
	}

	private fun SecretBox.asEntity(folderId: Long) = FolderKeyEntity(
		folderId = folderId,
		kdfMemoryKib = params.memoryKib,
		kdfIterations = params.iterations,
		kdfParallelism = params.parallelism,
		salt = salt,
		sealed = sealed,
		biometric = null,
		removing = false,
		createdAt = System.currentTimeMillis(),
	)

	private fun FolderKeyEntity.box() =
		SecretBox(KdfParams(kdfMemoryKib, kdfIterations, kdfParallelism), salt, sealed)
}
