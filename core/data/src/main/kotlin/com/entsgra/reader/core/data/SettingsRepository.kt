package com.entsgra.reader.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.entsgra.reader.core.model.AppTheme
import com.entsgra.reader.core.model.LibrarySort
import com.entsgra.reader.core.model.LockTimeout
import com.entsgra.reader.core.model.ReadingTextSize
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * App-wide preferences. Stored unencrypted, so nothing about books or folders may go here;
 * that belongs in the encrypted database.
 */
@Singleton
class SettingsRepository @Inject constructor(
	private val store: DataStore<Preferences>,
) {
	/** Never fails: an unreadable file falls back to defaults, since launch waits on this. */
	val theme: Flow<AppTheme> = store.data
		.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
		.map { prefs ->
			prefs[THEME]?.let { name -> AppTheme.entries.firstOrNull { it.name == name } } ?: AppTheme.LIGHT
		}
		.distinctUntilChanged()

	suspend fun setTheme(theme: AppTheme) {
		store.edit { it[THEME] = theme.name }
	}

	val librarySort: Flow<LibrarySort> = store.data
		.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
		.map { prefs ->
			prefs[LIBRARY_SORT]?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }
				?: LibrarySort.TITLE
		}
		.distinctUntilChanged()

	suspend fun setLibrarySort(sort: LibrarySort) {
		store.edit { it[LIBRARY_SORT] = sort.name }
	}

	val readingTextSize: Flow<ReadingTextSize> = store.data
		.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
		.map { prefs ->
			prefs[READING_TEXT_SIZE]?.let { name -> ReadingTextSize.entries.firstOrNull { it.name == name } }
				?: ReadingTextSize.MEDIUM
		}
		.distinctUntilChanged()

	suspend fun setReadingTextSize(size: ReadingTextSize) {
		store.edit { it[READING_TEXT_SIZE] = size.name }
	}

	val lockTimeout: Flow<LockTimeout> = store.data
		.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
		.map { prefs ->
			prefs[LOCK_TIMEOUT]?.let { name -> LockTimeout.entries.firstOrNull { it.name == name } }
				?: LockTimeout.ONE_MINUTE
		}
		.distinctUntilChanged()

	suspend fun setLockTimeout(timeout: LockTimeout) {
		store.edit { it[LOCK_TIMEOUT] = timeout.name }
	}

	/** Screenshots and the recents preview blocked on every screen, not only locked content. */
	val blockScreenshots: Flow<Boolean> = store.data
		.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
		.map { prefs -> prefs[BLOCK_SCREENSHOTS] ?: false }
		.distinctUntilChanged()

	suspend fun setBlockScreenshots(block: Boolean) {
		store.edit { it[BLOCK_SCREENSHOTS] = block }
	}

	private companion object {
		val THEME = stringPreferencesKey("theme")
		val LIBRARY_SORT = stringPreferencesKey("library_sort")
		val READING_TEXT_SIZE = stringPreferencesKey("reading_text_size")
		val LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
		val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
	}
}
