package com.sreecharan.brainrot_buddies_steal_battle.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sreecharan.brainrot_buddies_steal_battle.billing.di.BillingPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hat entitlements, persisted deterministically in our own Preferences
 * DataStore. The remove-ads flag lives canonically in GamePrefs
 * (`ads_removed`, Builder A); this store covers the three hats only.
 */
interface BillingEntitlementStore {
    val ownedHats: Flow<Set<String>>
    suspend fun grantHat(hatId: String)
}

@Singleton
class DataStoreBillingEntitlementStore @Inject constructor(
    @BillingPreferences private val store: DataStore<Preferences>,
) : BillingEntitlementStore {

    override val ownedHats: Flow<Set<String>> =
        store.data.map { it[KEY_OWNED_HATS] ?: emptySet() }

    override suspend fun grantHat(hatId: String) {
        store.edit { prefs ->
            prefs[KEY_OWNED_HATS] = (prefs[KEY_OWNED_HATS] ?: emptySet()) + hatId
        }
    }

    companion object {
        private val KEY_OWNED_HATS = stringSetPreferencesKey("owned_hats")
    }
}
