package com.entsgra.reader.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
	@Provides
	@Singleton
	fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
		PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
