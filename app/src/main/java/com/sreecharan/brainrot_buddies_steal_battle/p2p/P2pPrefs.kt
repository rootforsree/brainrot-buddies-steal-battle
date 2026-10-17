package com.sreecharan.brainrot_buddies_steal_battle.p2p

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sreecharan.brainrot_buddies_steal_battle.p2p.di.P2pPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2P-scoped flags, kept in our own Preferences DataStore (Builder A's
 * GamePrefs stays untouched):
 * - first-heist ceremony flag (pairing ritual, §1 Social)
 * - viewed incoming-raid ids (villain cards show once)
 * - shadow-sync watermarks (each pending shadow raid is replayed to the
 *   victim exactly once)
 */
@Singleton
class P2pPrefs
    @Inject
    constructor(
        @P2pPreferences private val store: DataStore<Preferences>,
    ) {
        val firstHeistCelebrated: Flow<Boolean> =
            store.data.map { it[KEY_FIRST_HEIST] ?: false }

        val viewedRaidIds: Flow<Set<String>> =
            store.data.map { it[KEY_VIEWED_RAIDS] ?: emptySet() }

        suspend fun setFirstHeistCelebrated() {
            store.edit { it[KEY_FIRST_HEIST] = true }
        }

        suspend fun isRaidViewed(raidId: String): Boolean =
            store.data
                .map { it[KEY_VIEWED_RAIDS] ?: emptySet() }
                .first()
                .contains(raidId)

        suspend fun markRaidViewed(raidId: String) {
            store.edit { prefs ->
                prefs[KEY_VIEWED_RAIDS] = (prefs[KEY_VIEWED_RAIDS] ?: emptySet()) + raidId
            }
        }

        suspend fun isShadowSynced(raidId: String): Boolean =
            store.data
                .map { it[KEY_SHADOW_SYNCED] ?: emptySet() }
                .first()
                .contains(raidId)

        suspend fun markShadowSynced(raidId: String) {
            store.edit { prefs ->
                prefs[KEY_SHADOW_SYNCED] = (prefs[KEY_SHADOW_SYNCED] ?: emptySet()) + raidId
            }
        }

        companion object {
            private val KEY_FIRST_HEIST = booleanPreferencesKey("first_heist_celebrated")
            private val KEY_VIEWED_RAIDS = stringSetPreferencesKey("viewed_raid_ids")
            private val KEY_SHADOW_SYNCED = stringSetPreferencesKey("shadow_synced_ids")
        }
    }
