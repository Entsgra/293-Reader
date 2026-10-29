package com.entsgra.reader.core.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * Held while files are written and their rows committed: importing, re-encrypting under another
 * key, and the orphan sweep. The sweep deletes files no row names, so it must never see a file
 * whose row is still to come.
 */
@Singleton
class StorageLock @Inject constructor() {
	val mutex = Mutex()
}
