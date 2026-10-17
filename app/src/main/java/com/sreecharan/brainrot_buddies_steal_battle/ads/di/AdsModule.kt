package com.sreecharan.brainrot_buddies_steal_battle.ads.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sreecharan.brainrot_buddies_steal_battle.ads.AdThrottle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AdsPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Builder D's own Hilt module for rewarded ads.
 * Does not touch di/RepositoryModule.kt.
 */
@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    private val Context.adsPreferences by preferencesDataStore(name = "ads_prefs")

    @Provides
    @Singleton
    @AdsPreferences
    fun provideAdsPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.adsPreferences

    @Provides
    @Singleton
    fun provideAdThrottle(): AdThrottle = AdThrottle()

    /**
     * AdMob show() must run on the main thread; this is an Android API
     * requirement, not a threading choice.
     */
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
