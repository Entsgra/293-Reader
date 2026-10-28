package com.entsgra.reader.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.entsgra.reader.core.security.DatabaseKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the SQLCipher-encrypted database off the main thread. Unwrapping the key touches
 * Keystore, which can take tens of milliseconds, so nothing may open the database eagerly
 * during injection. Callers suspend on [get].
 */
@Singleton
class DatabaseProvider @Inject constructor(
	@ApplicationContext private val context: Context,
	private val key: DatabaseKey,
) {
	private val mutex = Mutex()

	@Volatile
	private var database: ReaderDatabase? = null

	suspend fun get(): ReaderDatabase = database ?: mutex.withLock {
		database ?: withContext(Dispatchers.IO) { open() }.also { database = it }
	}

	private fun open(): ReaderDatabase {
		System.loadLibrary("sqlcipher")
		// The factory keeps the passphrase: WAL opens extra reader connections that need it.
		val factory = SupportOpenHelperFactory(key.passphrase())
		return Room.databaseBuilder(context, ReaderDatabase::class.java, NAME)
			.openHelperFactory(factory)
			.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
			.addMigrations(*ALL_MIGRATIONS)
			.build()
			.also { it.openHelper.writableDatabase } // Open now, on IO, so key errors surface here.
	}

	private companion object {
		const val NAME = "reader.db"
	}
}
