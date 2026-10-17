package com.sreecharan.brainrot_buddies_steal_battle.p2p.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class P2pPreferences

/**
 * Builder D's own Hilt module for P2P-scoped persistence.
 * Does not touch di/RepositoryModule.kt or GamePrefs.
 */
@Module
@InstallIn(SingletonComponent::class)
object P2pModule {
    private val Context.p2pPreferences by preferencesDataStore(name = "p2p_prefs")

    @Provides
    @Singleton
    @P2pPreferences
    fun provideP2pPreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.p2pPreferences
}
