package com.entsgra.reader.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.entsgra.reader.core.security.AppLockStore
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
 * during injection. Callers suspend on [get], which also waits for the app lock to open when
 * there is one.
 */
@Singleton
class DatabaseProvider @Inject constructor(
	@ApplicationContext private val context: Context,
	private val key: DatabaseKey,
	private val appLock: AppLockStore,
) {
	private val mutex = Mutex()

	@Volatile
	private var database: ReaderDatabase? = null

	suspend fun get(): ReaderDatabase = database ?: run {
		appLock.awaitOpen()
		mutex.withLock {
			database ?: withContext(Dispatchers.IO) { open() }.also { database = it }
		}
	}

	/**
	 * Folds the write-ahead log back into the database file and truncates it, so text that was
	 * just sealed or deleted does not linger in the log. Blocking.
	 */
	fun checkpoint(db: ReaderDatabase) {
		db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
	}

	private fun open(): ReaderDatabase {
		System.loadLibrary("sqlcipher")
		// The factory keeps the passphrase: WAL opens extra reader connections that need it.
		val factory = SupportOpenHelperFactory(key.passphrase())
		return Room.databaseBuilder(context, ReaderDatabase::class.java, NAME)
			.openHelperFactory(factory)
			.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
			.addMigrations(*ALL_MIGRATIONS)
			.addCallback(SecureDelete)
			.build()
			.also { it.openHelper.writableDatabase } // Open now, on IO, so key errors surface here.
	}

	/** Overwrites deleted content, so a title replaced by its sealed form leaves no copy in free pages. */
	private object SecureDelete : RoomDatabase.Callback() {
		override fun onOpen(db: SupportSQLiteDatabase) {
			db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
		}
	}

	private companion object {
		const val NAME = "reader.db"
	}
}
