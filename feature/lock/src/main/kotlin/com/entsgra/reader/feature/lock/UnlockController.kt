package com.entsgra.reader.feature.lock

import com.entsgra.reader.core.data.lock.BiometricStart
import com.entsgra.reader.core.data.lock.UnlockResult
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What unlocking needs from a lock: a folder's or the app's. */
interface UnlockOps {
	suspend fun unlock(secret: CharArray): UnlockResult

	suspend fun waitMs(): Long

	suspend fun hasBiometric(): Boolean

	suspend fun biometricStart(): BiometricStart

	suspend fun biometricUnlock(cipher: Cipher)

	suspend fun biometricEnrollCipher(): Cipher

	suspend fun biometricEnroll(cipher: Cipher)
}

/**
 * The unlock screen's state. Holds no secret: what was typed goes straight to [UnlockOps] as a
 * char array, which is zeroed there.
 */
data class UnlockUiState(
	val busy: Boolean = false,
	val wrong: Boolean = false,
	val waitSeconds: Int = 0,
	val biometric: Boolean = false,
	/** Fingerprints changed and the biometric wrap went; say so and offer it again after. */
	val invalidated: Boolean = false,
	val offerBiometric: Boolean = false,
)

/** Drives an unlock screen. Lives in a ViewModel's scope. */
class UnlockController(private val scope: CoroutineScope, private val ops: UnlockOps) {
	private val _state = MutableStateFlow(UnlockUiState())
	val state: StateFlow<UnlockUiState> = _state.asStateFlow()
	private var countdown: Job? = null

	init {
		refresh()
	}

	/** Re-reads the wait and whether biometrics are set up, e.g. when the screen shows again. */
	fun refresh() {
		scope.launch {
			startCountdown(ops.waitMs())
			_state.update { it.copy(biometric = ops.hasBiometric()) }
		}
	}

	fun submit(secret: String) {
		val current = _state.value
		if (current.busy || current.waitSeconds > 0 || secret.isEmpty()) return
		_state.update { it.copy(busy = true, wrong = false) }
		scope.launch {
			val result = ops.unlock(secret.toCharArray())
			_state.update { it.copy(busy = false) }
			when (result) {
				UnlockResult.Ok -> _state.update {
					it.copy(offerBiometric = it.invalidated, invalidated = false, wrong = false)
				}
				is UnlockResult.Wrong -> {
					_state.update { it.copy(wrong = true) }
					startCountdown(result.waitMs)
				}
				is UnlockResult.Wait -> startCountdown(result.waitMs)
			}
		}
	}

	/** The cipher for the prompt, or null: not set up, or invalidated (then the state says so). */
	suspend fun biometricCipher(): Cipher? = when (val start = ops.biometricStart()) {
		is BiometricStart.Ready -> start.cipher
		BiometricStart.None -> null.also { _state.update { it.copy(biometric = false) } }
		BiometricStart.Invalidated -> null.also { _state.update { it.copy(biometric = false, invalidated = true) } }
	}

	fun biometricMatched(cipher: Cipher) {
		scope.launch {
			_state.update { it.copy(busy = true) }
			try {
				ops.biometricUnlock(cipher)
			} finally {
				_state.update { it.copy(busy = false) }
			}
		}
	}

	suspend fun enrollCipher(): Cipher = ops.biometricEnrollCipher()

	fun enrolled(cipher: Cipher) {
		scope.launch {
			ops.biometricEnroll(cipher)
			_state.update { it.copy(offerBiometric = false, biometric = true) }
		}
	}

	fun declineBiometric() = _state.update { it.copy(offerBiometric = false) }

	private fun startCountdown(waitMs: Long) {
		countdown?.cancel()
		if (waitMs <= 0) {
			_state.update { it.copy(waitSeconds = 0) }
			return
		}
		countdown = scope.launch {
			var left = waitMs
			while (left > 0) {
				_state.update { it.copy(waitSeconds = ((left + 999) / 1000).toInt()) }
				val step = minOf(left, TICK_MS)
				delay(step)
				left -= step
			}
			_state.update { it.copy(waitSeconds = 0) }
		}
	}

	private companion object {
		const val TICK_MS = 1_000L
	}
}

/** "0:28", "4:00", "1:00:00". */
fun formatWait(seconds: Int): String {
	val h = seconds / 3600
	val m = seconds % 3600 / 60
	val s = seconds % 60
	return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
