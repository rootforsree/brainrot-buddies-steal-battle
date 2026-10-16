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
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.AMBIENT_CAP_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.CALM_BASS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.CALM_MELODY
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.CALM_SPARKLE
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.COIN_TICK_RATE_PITCH
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_ATTACK_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_FACTOR
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_RELEASE_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_STEP_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_WINDOW_REWARD_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_WINDOW_STING_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.DUCK_WINDOW_VOICE_EXACT
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.JITTER_MAX
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.JITTER_MIN
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.MAX_SIMULTANEOUS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.MIN_PLAYABLE_VOLUME
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.RAMP_MS_STING
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.UPGRADE_PITCH_PER_LEVEL
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.VOICE_DUCK_BACKSTOP_MS
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.VOLUME_SCALE
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager.Companion.WIND_DOWN_MS
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
import kotlin.random.Random

/**
 * Shared mutable audio state behind [SoundManager]'s channel seams: the
 * §9 playback rules (priority cap, ducking, jitter, ramps, stem mixing).
 * One instance per [SoundManager]; the channel implementations below are
 * thin facades over it.
 */
internal class SoundPlaybackCore @Inject constructor(
    val engine: AudioEngine,
    gamePrefs: GamePrefs,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Contract §9: no audio before the first tap. Set by [AudioLifecycle.unlockAudio]. */
    val unlocked = AtomicBoolean(false)

    var musicVolume = 1f
        private set
    var sfxVolume = 1f
        private set
    var voiceVolume = 1f
        private set

    /** Insertion-ordered; the 8-sound cap evicts lowest priority, oldest first. */
    val activeSounds = LinkedHashMap<Int, ActiveSound>()

    /** Ducking tokens currently holding the music down. */
    val duckTokens = mutableSetOf<Any>()
    var duckJob: Job? = null
    var duckFactor = 1f

    var raidMode = false
    var eventMode = false
    var windDown = false
    var windDownJob: Job? = null

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

    data class ActiveSound(
        val kind: SoundKind,
        val startedAtMs: Long,
    )

    private data class DuckToken(val streamId: Int)

    private data class PlayRequest(
        val soundId: Int,
        val volume: Float,
        val pitch: Float,
    )

    fun play(spec: SoundSpec, pitch: Float = 1f, volumeScale: Float = 1f) {
        if (!unlocked.get() || engine.isMuted()) return
        val request = preparePlay(spec, pitch, volumeScale) ?: return
        launchStream(spec, request)
    }

    private fun preparePlay(spec: SoundSpec, pitch: Float, volumeScale: Float): PlayRequest? {
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
        if (finalVolume <= MIN_PLAYABLE_VOLUME) return null
        val soundId = engine.loadOneShot(spec.resName)
        if (soundId <= 0) return null
        return PlayRequest(soundId, finalVolume.coerceIn(0f, 1f), finalPitch)
    }

    private fun launchStream(spec: SoundSpec, request: PlayRequest) {
        val admitted = synchronized(activeSounds) { evictIfFull(spec.kind) }
        if (!admitted) return
        val streamId = engine.playOneShot(
            soundId = request.soundId,
            volume = request.volume,
            pitch = request.pitch,
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

    /**
     * Makes room for one more stream under the 8-sound cap. Returns false when
     * no stream may be evicted (every active stream outranks the incoming
     * one): the incoming sound is then dropped, never forced in.
     *
     * Eviction order is lowest priority first, oldest first. A voice is
     * never evicted for a lower kind; a new voice evicts the oldest voice
     * only when every active stream is a voice.
     */
    fun evictIfFull(incoming: SoundKind): Boolean {
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

    /** -6 dB ducking under voices/stings, 0.8 s ease-back. */
    fun holdDuck(windowMs: Long, streamId: Int) {
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

    fun onVoiceEnded(streamId: Int) {
        synchronized(activeSounds) { activeSounds.remove(streamId) }
        val token = synchronized(duckTokens) {
            duckTokens.firstOrNull { it is DuckToken && it.streamId == streamId }
        }
        if (token != null) releaseDuck(token)
    }

    fun releaseDuck(token: Any) {
        val removed = synchronized(duckTokens) { duckTokens.remove(token) }
        if (removed) applyDucking()
    }

    fun applyDucking() {
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

    fun updateStemVolumes() {
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

    fun scheduleWindDown() {
        windDownJob?.cancel()
        windDownJob = scope.launch {
            delay(WIND_DOWN_MS)
            windDown = true
            updateStemVolumes()
        }
    }
}

internal class SfxPlayerImpl @Inject constructor(
    private val core: SoundPlaybackCore,
) : SfxPlayer {

    override fun playClaimChime() = core.play(REWARD_SPECS.getValue(SFX_CLAIM_CHIME))
    override fun playCoinTick(incomeRate: Float) =
        core.play(
            AMBIENT_SPECS.getValue(SFX_COIN_TICK),
            pitch = (1f + incomeRate * COIN_TICK_RATE_PITCH).coerceAtMost(2f),
        )
    override fun playStealSuccess() = core.play(REWARD_SPECS.getValue(SFX_STEAL_SUCCESS))
    override fun playStealFail() = core.play(REWARD_SPECS.getValue(SFX_STEAL_FAIL))
    override fun playUpgrade(level: Int) =
        core.play(STING_SPECS.getValue(SFX_UPGRADE), pitch = 1f + (level - 1) * UPGRADE_PITCH_PER_LEVEL)
    override fun playShieldBreak() = core.play(REWARD_SPECS.getValue(SFX_SHIELD_BREAK))
    override fun playButton() = core.play(AMBIENT_SPECS.getValue(SFX_BUTTON))
    override fun playVaultCollect() = core.play(STING_SPECS.getValue(SFX_VAULT_COLLECT))
    override fun playGiftOpen() = core.play(STING_SPECS.getValue(SFX_GIFT_OPEN))
    override fun playRaidBell() = core.play(STING_SPECS.getValue(SFX_RAID_BELL))
}

internal class VoicePlayerImpl @Inject constructor(
    private val core: SoundPlaybackCore,
) : VoicePlayer {

    override fun playVoice(voiceResName: String) {
        if (!core.unlocked.get() || core.engine.isMuted() || core.voiceVolume <= MIN_PLAYABLE_VOLUME) {
            return
        }
        val streamId = core.engine.playVoice(voiceResName, core.voiceVolume, RAMP_MS_STING)
        if (streamId > 0) admitVoiceStream(streamId)
    }

    private fun admitVoiceStream(streamId: Int) {
        val admitted = synchronized(core.activeSounds) { core.evictIfFull(SoundKind.VOICE) }
        if (admitted) {
            synchronized(core.activeSounds) {
                core.activeSounds[streamId] =
                    SoundPlaybackCore.ActiveSound(SoundKind.VOICE, nowMs())
            }
            core.holdDuck(DUCK_WINDOW_VOICE_EXACT, streamId)
        } else {
            core.engine.stopVoice(streamId)
        }
    }
}

internal class MusicControllerImpl @Inject constructor(
    private val core: SoundPlaybackCore,
) : MusicController {

    override fun startMusic() {
        if (!core.unlocked.get()) return
        core.scope.launch {
            STEMS.indices.forEach { core.engine.loadStem(it) }
            core.engine.startStems()
            core.updateStemVolumes()
            core.scheduleWindDown()
        }
    }

    override fun stopMusic() {
        core.windDownJob?.cancel()
        core.scope.launch { core.engine.stopStems() }
    }

    override fun setRaidMode(active: Boolean) {
        core.raidMode = active
        core.updateStemVolumes()
    }

    override fun setEventMode(active: Boolean) {
        core.eventMode = active
        core.updateStemVolumes()
    }

    override fun pokeActivity() {
        if (core.windDown) {
            core.windDown = false
            core.updateStemVolumes()
        }
        core.scheduleWindDown()
    }
}

internal class SoundLifecycleImpl @Inject constructor(
    private val core: SoundPlaybackCore,
) : AudioLifecycle {

    override fun unlockAudio() {
        core.unlocked.set(true)
    }

    override fun isUnlocked(): Boolean = core.unlocked.get()

    override fun release() {
        core.windDownJob?.cancel()
        core.duckJob?.cancel()
        core.scope.launch { core.engine.stopStems() }
        core.scope.cancel()
        core.engine.release()
    }
}

internal val STING_SPECS = mapOf(
    SFX_UPGRADE to SoundSpec(SFX_UPGRADE, SoundKind.STING, AudioChannel.SOUNDS),
    SFX_VAULT_COLLECT to SoundSpec(SFX_VAULT_COLLECT, SoundKind.STING, AudioChannel.SOUNDS),
    SFX_GIFT_OPEN to SoundSpec(SFX_GIFT_OPEN, SoundKind.STING, AudioChannel.SOUNDS),
    SFX_RAID_BELL to SoundSpec(SFX_RAID_BELL, SoundKind.STING, AudioChannel.VOICES),
)
internal val REWARD_SPECS = mapOf(
    SFX_CLAIM_CHIME to SoundSpec(SFX_CLAIM_CHIME, SoundKind.REWARD, AudioChannel.SOUNDS),
    SFX_STEAL_SUCCESS to SoundSpec(SFX_STEAL_SUCCESS, SoundKind.REWARD, AudioChannel.SOUNDS),
    SFX_STEAL_FAIL to SoundSpec(SFX_STEAL_FAIL, SoundKind.REWARD, AudioChannel.SOUNDS),
    SFX_SHIELD_BREAK to SoundSpec(SFX_SHIELD_BREAK, SoundKind.REWARD, AudioChannel.SOUNDS),
)
internal val AMBIENT_SPECS = mapOf(
    SFX_COIN_TICK to SoundSpec(SFX_COIN_TICK, SoundKind.AMBIENT, AudioChannel.SOUNDS, 0.7f),
    SFX_BUTTON to SoundSpec(SFX_BUTTON, SoundKind.AMBIENT, AudioChannel.SOUNDS, 0.6f),
)
internal val PRELOAD_SPECS = (STING_SPECS.values + REWARD_SPECS.values + AMBIENT_SPECS.values)

internal fun rampFor(kind: SoundKind): Int = when (kind) {
    SoundKind.VOICE, SoundKind.STING -> RAMP_MS_STING
    SoundKind.REWARD, SoundKind.AMBIENT, SoundKind.MUSIC -> 0
}

internal fun oneShotCapMsFor(kind: SoundKind): Long = when (kind) {
    SoundKind.STING -> DUCK_WINDOW_STING_MS
    SoundKind.REWARD -> DUCK_WINDOW_REWARD_MS
    SoundKind.AMBIENT -> AMBIENT_CAP_MS
    SoundKind.VOICE, SoundKind.MUSIC -> 0L
}

/** Returns a signed deviation in [0.05, 0.08]: the 5-8% jitter band. */
internal fun jitter(): Float {
    val magnitude = JITTER_MIN + Random.nextFloat() * (JITTER_MAX - JITTER_MIN)
    return if (Random.nextBoolean()) magnitude else -magnitude
}

internal fun nowMs(): Long = System.currentTimeMillis()
