package com.sreecharan.brainrot_buddies_steal_battle.billing.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingEntitlementStore
import com.sreecharan.brainrot_buddies_steal_battle.billing.DataStoreBillingEntitlementStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingPreferences

/**
 * Builder D's own Hilt module for Play Billing.
 * Does not touch di/RepositoryModule.kt.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    private val Context.billingPreferences by preferencesDataStore(name = "billing_prefs")

    @Provides
    @Singleton
    @BillingPreferences
    fun provideBillingPreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.billingPreferences
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingBindingModule {
    @Binds
    abstract fun bindEntitlementStore(impl: DataStoreBillingEntitlementStore): BillingEntitlementStore
}
