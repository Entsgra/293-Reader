package com.entsgra.reader.core.data.lock

import com.entsgra.reader.core.security.SecretResult

/** Outcome of anything that checks a PIN or passphrase. */
sealed interface UnlockResult {
	data object Ok : UnlockResult

	/** Wrong secret; the next attempt may come after [waitMs]. */
	data class Wrong(val waitMs: Long) : UnlockResult

	/** Too many wrong attempts; nothing was tried. Try again after [waitMs]. */
	data class Wait(val waitMs: Long) : UnlockResult
}

internal fun SecretResult<*>.asUnlockResult(): UnlockResult = when (this) {
	is SecretResult.Ok -> UnlockResult.Ok
	is SecretResult.Wrong -> UnlockResult.Wrong(waitMs)
	is SecretResult.Wait -> UnlockResult.Wait(waitMs)
}

/** The book is in a locked folder that is closed. */
class BookLockedException : Exception("The book's folder is locked")
