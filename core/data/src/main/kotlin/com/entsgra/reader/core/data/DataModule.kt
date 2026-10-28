package com.entsgra.reader.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.entsgra.reader.reader.api.BitmapPool
import com.entsgra.reader.reader.api.BookFormat
import com.entsgra.reader.reader.api.BookFormatRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
	@Provides
	@Singleton
	@ApplicationScope
	fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	@Provides
	@Singleton
	fun provideFormatRegistry(formats: Set<@JvmSuppressWildcards BookFormat>) = BookFormatRegistry(formats)

	/** Idle page bitmaps kept for reuse: about four screen-sized pages. */
	@Provides
	@Singleton
	fun provideBitmapPool() = BitmapPool(maxBytes = 48L * 1024 * 1024)

	@Provides
	@Singleton
	fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
		PreferenceDataStoreFactory.create(
			// Only display preferences live here; resetting a corrupt file beats failing launch.
			corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
		) { context.preferencesDataStoreFile("settings") }
}

/** Format modules (`reader:*`) add themselves with `@IntoSet`; the app module pulls them in. */
@Module
@InstallIn(SingletonComponent::class)
internal interface FormatBindings {
	@Multibinds
	fun formats(): Set<BookFormat>
}
