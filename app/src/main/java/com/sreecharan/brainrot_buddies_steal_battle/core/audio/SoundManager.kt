package com.sreecharan.brainrot_buddies_steal_battle.core.audio

import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * The single audio brain, contract §9. Three independent channels
 * (Music/Sounds/Voices, 0-100 each, persisted), priority
 * voice > stings > reward SFX > ambient > music, an 8-sound cap with
 * priority eviction, 6 dB music ducking under voices/stings with a 0.8 s
 * ease-back, 5-8% pitch/volume jitter on utility SFX only (never voices or
 * jingles), a >= 100 ms ramp on every sting and voice, stem layering
 * (full band in raid windows/events, calm layer otherwise, wind-down to
 * calm after 20 minutes of continuous play), no audio before the first tap,
 * and hardware mute / Do Not Disturb honored on every play.
 *
 * All platform calls go through [AudioEngine], so these rules are plain
 * unit-testable logic (see SoundManagerTest). The channel implementations
 * live in SoundPlayback.kt; this class only wires them together with
 * interface delegation.
 */
@Singleton
class SoundManager
    @Inject
    constructor(
        engine: AudioEngine,
        gamePrefs: GamePrefs,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        core: SoundPlaybackCore = SoundPlaybackCore(engine, gamePrefs, ioDispatcher),
    ) : SfxPlayer by SfxPlayerImpl(core),
        VoicePlayer by VoicePlayerImpl(core),
        MusicController by MusicControllerImpl(core),
        AudioLifecycle by SoundLifecycleImpl(core) {
        companion object {
            const val MAX_SIMULTANEOUS = 8

            /** -6 dB as an amplitude multiplier. */
            val DUCK_FACTOR: Float = 10.0.pow(-6.0 / 20.0).toFloat()

            const val RAMP_MS_STING = 120
            const val DUCK_ATTACK_MS = 120L
            const val DUCK_RELEASE_MS = 800L
            internal const val DUCK_STEP_MS = 50L

            /** Voices hold the duck exactly until they end (with a backstop). */
            const val DUCK_WINDOW_VOICE_EXACT = -1L
            internal const val VOICE_DUCK_BACKSTOP_MS = 15_000L

            /** Stings/rewards hold the duck for a fixed window (no completion callback). */
            const val DUCK_WINDOW_STING_MS = 3_000L
            const val DUCK_WINDOW_REWARD_MS = 1_500L
            internal const val AMBIENT_CAP_MS = 500L

            internal const val JITTER_MIN = 0.05f
            internal const val JITTER_MAX = 0.08f
            internal const val MIN_PLAYABLE_VOLUME = 0.001f
            internal const val VOLUME_SCALE = 100f
            internal const val COIN_TICK_RATE_PITCH = 0.02f
            internal const val UPGRADE_PITCH_PER_LEVEL = 0.12f

            /** Calm-layer stem mix (contract §9: strip to ukulele + shaker). */
            internal const val CALM_BASS = 0.2f
            internal const val CALM_MELODY = 0.9f
            internal const val CALM_SPARKLE = 0.5f

            internal const val WIND_DOWN_MS = 20 * 60 * 1000L
        }
    }
