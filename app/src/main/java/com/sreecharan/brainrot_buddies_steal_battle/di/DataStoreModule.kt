package com.sreecharan.brainrot_buddies_steal_battle.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.SettingsProto
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.SettingsSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private val Context.settingsDataStore by dataStore(
        fileName = "settings.pb",
        serializer = SettingsSerializer,
    )

    private val Context.gamePrefsDataStore by preferencesDataStore(
        name = "game_prefs",
    )

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<SettingsProto> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideGamePrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.gamePrefsDataStore
}
