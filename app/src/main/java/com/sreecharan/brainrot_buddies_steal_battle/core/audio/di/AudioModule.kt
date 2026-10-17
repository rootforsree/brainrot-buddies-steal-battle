package com.sreecharan.brainrot_buddies_steal_battle.core.audio.di

import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AndroidAudioEngine
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wiring for Builder B's audio stack. Channel volumes come from Builder A's
 * `GamePrefs` (contract §5 keys `music_vol`/`sfx_vol`/`voice_vol`), which
 * `SoundManager` injects directly.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindAudioEngine(impl: AndroidAudioEngine): AudioEngine
}
