package com.sreecharan.brainrot_buddies_steal_battle.core.audio

import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_BUTTON
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_CLAIM_CHIME
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_COIN_TICK
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_GIFT_OPEN
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_RAID_BELL
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_SHIELD_BREAK
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_STEAL_FAIL
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_STEAL_SUCCESS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_UPGRADE
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.SFX_VAULT_COLLECT
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.STEMS
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.random.Random

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
 * unit-testable logic (see SoundManagerTest).
 */
@Singleton
class SoundManager @Inject constructor(
    private val engine: AudioEngine,
    gamePrefs: GamePrefs,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Contract §9: no audio before the first tap. Set by [unlockAudio]. */
    private val unlocked = AtomicBoolean(false)

    private var musicVolume = 1f
    private var sfxVolume = 1f
    private var voiceVolume = 1f

    private data class ActiveSound(
        val kind: SoundKind,
        val startedAtMs: Long,
    )

    /** Insertion-ordered; the 8-sound cap evicts lowest priority, oldest first. */
    private val activeSounds = LinkedHashMap<Int, ActiveSound>()

    /** Ducking tokens currently holding the music down. */
    private val duckTokens = mutableSetOf<Any>()
    private var duckJob: Job? = null
    private var duckFactor = 1f

    private var raidMode = false
    private var eventMode = false
    private var windDown = false
    private var windDownJob: Job? = null

    init {
        scope.launch {
            // Channel volumes live in Builder A's GamePrefs (contract §5
            // keys `music_vol`/`sfx_vol`/`voice_vol`), 0-100 each.
            combine(gamePrefs.musicVolume, gamePrefs.sfxVolume, gamePrefs.voiceVolume) { m, s, v ->
                Triple(m, s, v)
            }.collect { (m, s, v) ->
                musicVolume = (m / VOLUME_SCALE).coerceIn(0f, 1f)
                sfxVolume = (s / VOLUME_SCALE).coerceIn(0f, 1f)
                voiceVolume = (v / VOLUME_SCALE).coerceIn(0f, 1f)
                updateStemVolumes()
            }
        }
        engine.setOnVoiceEnd { streamId -> onVoiceEnded(streamId) }
        // Warm the SoundPool cache so the first real tap answers in <100 ms.
        scope.launch {
            PRELOAD_SPECS.forEach { engine.loadOneShot(it.resName) }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Call on the first user tap anywhere. Before this, everything is silent. */
    fun unlockAudio() {
        unlocked.set(true)
    }

    fun isUnlocked(): Boolean = unlocked.get()

    fun release() {
        windDownJob?.cancel()
        duckJob?.cancel()
        scope.launch { engine.stopStems() }
        scope.cancel()
        engine.release()
    }

    // ------------------------------------------------------------------
    // One-shots: the §9 SFX hooks
    // ------------------------------------------------------------------

    fun playClaimChime() = play(REWARD_SPECS.getValue(SFX_CLAIM_CHIME))
    fun playCoinTick(incomeRate: Float = 1f) =
        play(AMBIENT_SPECS.getValue(SFX_COIN_TICK), pitch = (1f + incomeRate * COIN_TICK_RATE_PITCH).coerceAtMost(2f))
    fun playStealSuccess() = play(REWARD_SPECS.getValue(SFX_STEAL_SUCCESS))
    fun playStealFail() = play(REWARD_SPECS.getValue(SFX_STEAL_FAIL))
    fun playUpgrade(level: Int) =
        play(STING_SPECS.getValue(SFX_UPGRADE), pitch = 1f + (level - 1) * UPGRADE_PITCH_PER_LEVEL)
    fun playShieldBreak() = play(REWARD_SPECS.getValue(SFX_SHIELD_BREAK))
    fun playButton() = play(AMBIENT_SPECS.getValue(SFX_BUTTON))
    fun playVaultCollect() = play(STING_SPECS.getValue(SFX_VAULT_COLLECT))
    fun playGiftOpen() = play(STING_SPECS.getValue(SFX_GIFT_OPEN))
    fun playRaidBell() = play(STING_SPECS.getValue(SFX_RAID_BELL))

    /** Plays `voice_<buddyId>` (or `voice_benny` etc.). Never jittered, always ramped. */
    fun playVoice(voiceResName: String) {
        if (!unlocked.get() || engine.isMuted()) return
        val volume = voiceVolume
        if (volume <= MIN_PLAYABLE_VOLUME) return
        val streamId = engine.playVoice(voiceResName, volume, RAMP_MS_STING)
        if (streamId <= 0) return
        val admitted = synchronized(activeSounds) { evictIfFull(SoundKind.VOICE) }
        if (!admitted) {
            engine.stopVoice(streamId)
            return
        }
        synchronized(activeSounds) {
            activeSounds[streamId] = ActiveSound(SoundKind.VOICE, nowMs())
        }
        holdDuck(DUCK_WINDOW_VOICE_EXACT, streamId)
    }

    // ------------------------------------------------------------------
    // Music stems
    // ------------------------------------------------------------------

    /** Starts the §9 toy-pop loop. Full band only in raid/event mode. */
    fun startMusic() {
        if (!unlocked.get()) return
        scope.launch {
            STEMS.indices.forEach { engine.loadStem(it) }
            engine.startStems()
            updateStemVolumes()
            scheduleWindDown()
        }
    }

    fun stopMusic() {
        windDownJob?.cancel()
        scope.launch { engine.stopStems() }
    }

    /** Contract §9: full band during raid windows. */
    fun setRaidMode(active: Boolean) {
        raidMode = active
        updateStemVolumes()
    }

    /** Zara's Saturday event: full band, event layer. Same stems, event-only. */
    fun setEventMode(active: Boolean) {
        eventMode = active
        updateStemVolumes()
    }

    /** Any real interaction resets the 20-minute wind-down clock. */
    fun pokeActivity() {
        if (windDown) {
            windDown = false
            updateStemVolumes()
        }
        scheduleWindDown()
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun play(spec: SoundSpec, pitch: Float = 1f, volumeScale: Float = 1f) {
        if (!unlocked.get() || engine.isMuted()) return
        val channelVol = when (spec.channel) {
            AudioChannel.MUSIC -> musicVolume
            AudioChannel.SOUNDS -> sfxVolume
            AudioChannel.VOICES -> voiceVolume
        }
        var finalPitch = pitch
        var finalVolume = channelVol * spec.baseVolume * volumeScale
        if (spec.kind == SoundKind.AMBIENT) {
            // 5-8% jitter on utility sounds ONLY. Never voices, never jingles.
            finalPitch *= 1f + jitter()
            finalVolume *= 1f + jitter()
        }
        if (finalVolume <= MIN_PLAYABLE_VOLUME) return
        val soundId = engine.loadOneShot(spec.resName)
        if (soundId <= 0) return
        val admitted = synchronized(activeSounds) { evictIfFull(spec.kind) }
        if (!admitted) return
        val streamId = engine.playOneShot(
            soundId = soundId,
            volume = finalVolume.coerceIn(0f, 1f),
            pitch = finalPitch,
            rampMs = rampFor(spec.kind),
            priority = spec.kind.priority,
        )
        if (streamId <= 0) return
        synchronized(activeSounds) {
            activeSounds[streamId] = ActiveSound(spec.kind, nowMs())
        }
        // SoundPool reports no completion, so one-shots retire from the cap
        // after a per-kind ceiling. The cap stays a "simultaneous" cap.
        scope.launch {
            delay(oneShotCapMsFor(spec.kind))
            synchronized(activeSounds) { activeSounds.remove(streamId) }
        }
        if (spec.kind == SoundKind.STING) {
            holdDuck(DUCK_WINDOW_STING_MS, streamId)
        } else if (spec.kind == SoundKind.REWARD) {
            holdDuck(DUCK_WINDOW_REWARD_MS, streamId)
        }
    }

    private fun oneShotCapMsFor(kind: SoundKind): Long = when (kind) {
        SoundKind.STING -> DUCK_WINDOW_STING_MS
        SoundKind.REWARD -> DUCK_WINDOW_REWARD_MS
        SoundKind.AMBIENT -> AMBIENT_CAP_MS
        SoundKind.VOICE, SoundKind.MUSIC -> 0L
    }

    /** Makes room for one more stream under the 8-sound cap. Returns false when
     *  no stream may be evicted (every active stream outranks the incoming
     *  one): the incoming sound is then dropped, never forced in.
     *
     *  Eviction order is lowest priority first, oldest first. A voice is
     *  never evicted for a lower kind; a new voice evicts the oldest voice
     *  only when every active stream is a voice. */
    private fun evictIfFull(incoming: SoundKind): Boolean {
        while (activeSounds.size >= MAX_SIMULTANEOUS) {
            val victim = activeSounds.entries
                .sortedWith(compareBy({ it.value.kind.priority }, { it.value.startedAtMs }))
                .firstOrNull { entry ->
                    entry.value.kind.priority < incoming.priority ||
                        (incoming == SoundKind.VOICE && entry.value.kind == SoundKind.VOICE)
                } ?: return false
            activeSounds.remove(victim.key)
            engine.stopStream(victim.key)
            engine.stopVoice(victim.key)
        }
        return true
    }

    private fun rampFor(kind: SoundKind): Int = when (kind) {
        SoundKind.VOICE, SoundKind.STING -> RAMP_MS_STING
        SoundKind.REWARD, SoundKind.AMBIENT, SoundKind.MUSIC -> 0
    }

    /** Returns a signed deviation in [0.05, 0.08]: the 5-8% jitter band. */
    private fun jitter(): Float {
        val magnitude = JITTER_MIN + Random.nextFloat() * (JITTER_MAX - JITTER_MIN)
        return if (Random.nextBoolean()) magnitude else -magnitude
    }

    // ------------------------------------------------------------------
    // Ducking: -6 dB under voices/stings, 0.8 s ease-back
    // ------------------------------------------------------------------

    private fun holdDuck(windowMs: Long, streamId: Int) {
        val token = DuckToken(streamId)
        synchronized(duckTokens) { duckTokens.add(token) }
        applyDucking()
        scope.launch {
            if (windowMs == DUCK_WINDOW_VOICE_EXACT) {
                // Released by onVoiceEnded; the timeout below is only a backstop
                // in case the engine never reports completion.
                delay(VOICE_DUCK_BACKSTOP_MS)
            } else {
                delay(windowMs)
            }
            releaseDuck(token)
        }
    }

    private fun onVoiceEnded(streamId: Int) {
        synchronized(activeSounds) { activeSounds.remove(streamId) }
        val token = synchronized(duckTokens) {
            duckTokens.firstOrNull { it is DuckToken && it.streamId == streamId }
        }
        if (token != null) releaseDuck(token)
    }

    private fun releaseDuck(token: Any) {
        val removed = synchronized(duckTokens) { duckTokens.remove(token) }
        if (removed) applyDucking()
    }

    private fun applyDucking() {
        val target = if (synchronized(duckTokens) { duckTokens.isNotEmpty() }) {
            DUCK_FACTOR
        } else {
            1f
        }
        duckJob?.cancel()
        if (duckFactor == target) return
        val from = duckFactor
        // Duck fast (no startle gap), ease back slow: 0.8 s per contract §9.
        val durationMs = if (target < from) DUCK_ATTACK_MS else DUCK_RELEASE_MS
        duckJob = scope.launch {
            val steps = (durationMs / DUCK_STEP_MS).toInt().coerceAtLeast(1)
            repeat(steps) { step ->
                delay(DUCK_STEP_MS)
                duckFactor = from + (target - from) * (step + 1) / steps
                updateStemVolumes()
            }
            duckFactor = target
            updateStemVolumes()
        }
    }

    // ------------------------------------------------------------------
    // Stem mixing
    // ------------------------------------------------------------------

    private fun updateStemVolumes() {
        // Wind-down wins over raid/event: after 20 minutes of continuous play
        // the mix strips to the calm layer no matter what is happening.
        val fullBand = (raidMode || eventMode) && !windDown
        val mix = if (fullBand) {
            floatArrayOf(1f, 1f, 1f, 1f)
        } else {
            // Calm layer: ukulele lead + shaker sparkle, bass tucked low.
            floatArrayOf(0f, CALM_BASS, CALM_MELODY, CALM_SPARKLE)
        }
        for (i in STEMS.indices) {
            engine.setStemVolume(i, (mix[i] * musicVolume * duckFactor).coerceIn(0f, 1f))
        }
    }

    private fun scheduleWindDown() {
        windDownJob?.cancel()
        windDownJob = scope.launch {
            delay(WIND_DOWN_MS)
            windDown = true
            updateStemVolumes()
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private data class DuckToken(val streamId: Int)

    companion object {
        const val MAX_SIMULTANEOUS = 8

        /** -6 dB as an amplitude multiplier. */
        val DUCK_FACTOR: Float = 10.0.pow(-6.0 / 20.0).toFloat()

        const val RAMP_MS_STING = 120
        const val DUCK_ATTACK_MS = 120L
        const val DUCK_RELEASE_MS = 800L
        private const val DUCK_STEP_MS = 50L

        /** Voices hold the duck exactly until they end (with a backstop). */
        const val DUCK_WINDOW_VOICE_EXACT = -1L
        private const val VOICE_DUCK_BACKSTOP_MS = 15_000L

        /** Stings/rewards hold the duck for a fixed window (no completion callback). */
        const val DUCK_WINDOW_STING_MS = 3_000L
        const val DUCK_WINDOW_REWARD_MS = 1_500L
        private const val AMBIENT_CAP_MS = 500L

        private const val JITTER_MIN = 0.05f
        private const val JITTER_MAX = 0.08f
        private const val MIN_PLAYABLE_VOLUME = 0.001f
        private const val VOLUME_SCALE = 100f
        private const val COIN_TICK_RATE_PITCH = 0.02f
        private const val UPGRADE_PITCH_PER_LEVEL = 0.12f

        /** Calm-layer stem mix (contract §9: strip to ukulele + shaker). */
        private const val CALM_BASS = 0.2f
        private const val CALM_MELODY = 0.9f
        private const val CALM_SPARKLE = 0.5f

        private const val WIND_DOWN_MS = 20 * 60 * 1000L

        private val STING_SPECS = mapOf(
            SFX_UPGRADE to SoundSpec(SFX_UPGRADE, SoundKind.STING, AudioChannel.SOUNDS),
            SFX_VAULT_COLLECT to SoundSpec(SFX_VAULT_COLLECT, SoundKind.STING, AudioChannel.SOUNDS),
            SFX_GIFT_OPEN to SoundSpec(SFX_GIFT_OPEN, SoundKind.STING, AudioChannel.SOUNDS),
            SFX_RAID_BELL to SoundSpec(SFX_RAID_BELL, SoundKind.STING, AudioChannel.VOICES),
        )
        private val REWARD_SPECS = mapOf(
            SFX_CLAIM_CHIME to SoundSpec(SFX_CLAIM_CHIME, SoundKind.REWARD, AudioChannel.SOUNDS),
            SFX_STEAL_SUCCESS to SoundSpec(SFX_STEAL_SUCCESS, SoundKind.REWARD, AudioChannel.SOUNDS),
            SFX_STEAL_FAIL to SoundSpec(SFX_STEAL_FAIL, SoundKind.REWARD, AudioChannel.SOUNDS),
            SFX_SHIELD_BREAK to SoundSpec(SFX_SHIELD_BREAK, SoundKind.REWARD, AudioChannel.SOUNDS),
        )
        private val AMBIENT_SPECS = mapOf(
            SFX_COIN_TICK to SoundSpec(SFX_COIN_TICK, SoundKind.AMBIENT, AudioChannel.SOUNDS, 0.7f),
            SFX_BUTTON to SoundSpec(SFX_BUTTON, SoundKind.AMBIENT, AudioChannel.SOUNDS, 0.6f),
        )
        private val PRELOAD_SPECS = (STING_SPECS.values + REWARD_SPECS.values + AMBIENT_SPECS.values)
    }
}
