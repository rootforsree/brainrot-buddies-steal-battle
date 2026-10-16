package com.sreecharan.brainrot_buddies_steal_battle.di

import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefsImpl
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.SettingsRepository
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.SettingsRepositoryImpl
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Singleton
    @Binds
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    abstract fun bindGamePrefs(impl: GamePrefsImpl): GamePrefs
}
