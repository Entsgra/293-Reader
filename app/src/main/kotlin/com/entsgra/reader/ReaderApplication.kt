package com.entsgra.reader

import android.app.Application
import com.entsgra.reader.core.data.SettingsRepository
import com.entsgra.reader.core.database.DatabaseProvider
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

	private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onCreate() {
		super.onCreate()
		// Unwrap the key and open the encrypted database off the main thread while the first
		// frame draws, so the library's first query does not wait on Keystore.
		appScope.launch { databaseProvider.get() }
		// MainActivity holds its first frame until the theme is known; start the read now so
		// DataStore has it cached by then.
		appScope.launch { settings.theme.first() }
	}
}
