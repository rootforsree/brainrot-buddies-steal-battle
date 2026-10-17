package com.sreecharan.brainrot_buddies_steal_battle.ui.home.di

import com.sreecharan.brainrot_buddies_steal_battle.ui.home.BaseGameData
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.GameRepositoryAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [BaseGameData] to the [GameRepositoryAdapter], which maps Builder A's
 * `GameRepository`/`GameEngine` onto the base board's data seam.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataModule {
    @Binds
    @Singleton
    abstract fun bindBaseGameData(impl: GameRepositoryAdapter): BaseGameData
}
