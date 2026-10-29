package com.entsgra.reader

import android.app.Application
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.data.library.BookImporter
import com.entsgra.reader.core.database.DatabaseProvider
import com.entsgra.reader.reader.api.BitmapPool
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class ReaderApplication : Application() {
	@Inject lateinit var databaseProvider: DatabaseProvider
	@Inject lateinit var settings: SettingsRepository
	@Inject lateinit var importer: BookImporter
	@Inject lateinit var bitmapPool: BitmapPool
	@Inject lateinit var lockGuard: AppLockGuard

	private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onCreate() {
		super.onCreate()
		lockGuard.install(this, appScope)
		// Unwrap the key and open the encrypted database off the main thread while the first
		// frame draws, so the library's first query does not wait on Keystore. With the app
		// lock on, this waits for it to be unlocked.
		appScope.launch {
			databaseProvider.get()
			// Files a crash left behind without a database row.
			importer.sweepOrphans()
			// Covers and page counts for books imported before their format had a reader.
			importer.backfillMetadata()
		}
		// MainActivity holds its first frame until the theme is known; start the read now so
		// DataStore has it cached by then.
		appScope.launch { settings.theme.first() }
	}

	override fun onTrimMemory(level: Int) {
		super.onTrimMemory(level)
		// Idle page bitmaps are only a cache; give them back once the UI is hidden.
		if (level >= TRIM_MEMORY_UI_HIDDEN) bitmapPool.clear()
	}
}
