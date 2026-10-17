package com.sreecharan.brainrot_buddies_steal_battle.data.datastore

import androidx.datastore.core.DataStore
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * Domain-level app settings. The single source of truth for theme and haptics.
 */
data class AppSettings(
    val themeMode: ThemeMode,
    val hapticsEnabled: Boolean,
)

interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setHapticsEnabled(enabled: Boolean)
}

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<SettingsProto>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(SettingsSerializer.defaultValue)
            } else {
                throw e
            }
        }
        .map { proto ->
            AppSettings(
                themeMode = proto.themeMode.toDomain(),
                hapticsEnabled = proto.hapticsEnabled,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.updateData { current ->
            current.toBuilder().setThemeMode(mode.toProto()).build()
        }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.updateData { current ->
            current.toBuilder().setHapticsEnabled(enabled).build()
        }
    }

    private fun ThemeModeProto.toDomain(): ThemeMode = when (this) {
        ThemeModeProto.THEME_MODE_PROTO_UNSPECIFIED -> ThemeMode.SYSTEM
        ThemeModeProto.SYSTEM -> ThemeMode.SYSTEM
        ThemeModeProto.LIGHT -> ThemeMode.LIGHT
        ThemeModeProto.DARK -> ThemeMode.DARK
        ThemeModeProto.UNRECOGNIZED -> ThemeMode.SYSTEM
    }

    private fun ThemeMode.toProto(): ThemeModeProto = when (this) {
        ThemeMode.SYSTEM -> ThemeModeProto.SYSTEM
        ThemeMode.LIGHT -> ThemeModeProto.LIGHT
        ThemeMode.DARK -> ThemeModeProto.DARK
    }
}
