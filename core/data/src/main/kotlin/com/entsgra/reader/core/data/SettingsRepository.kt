package com.entsgra.reader.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.entsgra.reader.core.model.AppTheme
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

	private companion object {
		val THEME = stringPreferencesKey("theme")
	}
}
