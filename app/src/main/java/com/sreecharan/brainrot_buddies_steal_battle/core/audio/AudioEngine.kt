package com.sreecharan.brainrot_buddies_steal_battle.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets.STEMS
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin seam over Android audio so [SoundManager]'s rules (priority, ducking,
 * jitter, ramps) stay pure and unit-testable. Implementations must be
 * thread-safe; [SoundManager] calls from its own coroutine scope.
 *
 * One-shots (stings, reward SFX, ambient ticks) go through SoundPool with
 * exactly 8 streams — the contract's 8-sound cap. Voices go through a
 * dedicated MediaPlayer so completion is exact (drives the 6 dB ducking
 * release); a new voice replaces the current one, catchphrases never talk
 * over each other.
 *
 * Split into focused seams so no single interface exceeds the complexity
 * budget; [AudioEngine] composes them all.
 */
interface AudioEngine : OneShotEngine, VoiceEngine, MusicStemEngine {

    /** True when the device is muted or in Do Not Disturb (contract §9:
     *  honor hardware mute + DND). */
    fun isMuted(): Boolean

    fun release()
}

/** SoundPool one-shots: load, play, stop. */
interface OneShotEngine {

    /** Loads a SoundPool one-shot; returns a sound id, or -1 if the asset is
     *  missing (pre-merge) or failed to load. */
    fun loadOneShot(resName: String): Int

    /**
     * Plays a loaded one-shot. [volume] is 0..1, [pitch] the playback-rate
     * multiplier. [rampMs] fades the stream in over at least that long
     * (contract §9: every sting ramps in >= 100 ms, no startles). [priority]
     * is the [SoundKind] priority for cap eviction. Returns a stream id, or
     * -1 when playback could not start.
     */
    fun playOneShot(
        soundId: Int,
        volume: Float,
        pitch: Float,
        rampMs: Int,
        priority: Int,
    ): Int

    fun stopStream(streamId: Int)
}

/** Voice lines: one dedicated MediaPlayer, exact completion. */
interface VoiceEngine {

    /** Plays a voice line (MediaPlayer); returns a stream id, or -1. */
    fun playVoice(resName: String, volume: Float, rampMs: Int): Int

    fun stopVoice(streamId: Int)

    /** Fired on the engine's thread when a voice stream finishes (or is
     *  stopped). SoundPool one-shots have no completion callback; their
     *  ducking windows are time-boxed by [SoundManager] instead. */
    fun setOnVoiceEnd(listener: (streamId: Int) -> Unit)
}

/** Music stems: the §9 toy-pop loop layers. */
interface MusicStemEngine {

    /** Loads one music stem by index into [AudioAssets.STEMS]; -1 if missing. */
    fun loadStem(stemIndex: Int): Int

    /** Starts all loaded stems looping, as close to in-sync as the platform
     *  allows. */
    fun startStems()

    fun setStemVolume(stemIndex: Int, volume: Float)

    fun stopStems()
}

internal const val RAMP_STEP_MS = 25L
internal const val MIN_AUDIBLE_VOLUME = 0.01f
internal const val MIN_PITCH = 0.5f
internal const val MAX_PITCH = 2.0f

/**
 * Shared mutable audio state plus the fade/voice-completion plumbing.
 * Singleton-scoped so every engine delegate talks to the one SoundPool.
 */
@Singleton
internal class AudioEngineCore @Inject constructor(
    @ApplicationContext val context: Context,
) {

    val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(AndroidAudioEngine.MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    val audioManager: AudioManager? =
        context.getSystemService(AudioManager::class.java)

    val loadedSounds = ConcurrentHashMap<String, Int>()
    val nextStreamId = AtomicInteger(1)
    val streamToPoolId = ConcurrentHashMap<Int, Int>()

    val voiceLock = Any()
    var voicePlayer: MediaPlayer? = null
    var voiceStreamId: Int = -1
    var voiceEndListener: (Int) -> Unit = {}

    val stemPlayers = arrayOfNulls<MediaPlayer>(STEMS.size)

    fun finishVoice(streamId: Int) {
        val player: MediaPlayer?
        synchronized(voiceLock) {
            if (voiceStreamId != streamId) return
            player = voicePlayer
            voicePlayer = null
            voiceStreamId = -1
        }
        try {
            player?.stop()
        } catch (_: IllegalStateException) {
            // Already completed; release below still applies.
        }
        player?.release()
        voiceEndListener(streamId)
    }

    fun resIdFor(resName: String): Int =
        context.resources.getIdentifier(resName, "raw", context.packageName)

    /**
     * Best-effort fade-in on a daemon thread. SoundPool/MediaPlayer have no
     * native fade, so we step the per-stream volume; steps are small enough
     * to be click-free and the total is never shorter than [rampMs].
     */
    fun rampStream(poolStreamId: Int, targetVolume: Float, rampMs: Int) {
        fadeThread(rampMs) { fraction ->
            val v = (targetVolume * fraction).coerceIn(0f, 1f)
            soundPool.setVolume(poolStreamId, v, v)
        }
    }

    fun rampVoice(player: MediaPlayer, streamId: Int, targetVolume: Float, rampMs: Int) {
        fadeThread(rampMs) { fraction ->
            synchronized(voiceLock) {
                if (voiceStreamId != streamId) return@fadeThread
            }
            val v = (targetVolume * fraction).coerceIn(0f, 1f)
            player.setVolume(v, v)
        }
    }

    fun fadeThread(rampMs: Int, apply: (Float) -> Unit) {
        Thread {
            val steps = (rampMs / RAMP_STEP_MS).coerceAtLeast(1)
            for (step in 1..steps) {
                try {
                    Thread.sleep(RAMP_STEP_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                apply(step / steps.toFloat())
            }
        }.apply { isDaemon = true }.start()
    }

    fun isMuted(): Boolean {
        val manager = audioManager ?: return false
        return manager.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            isMusicStreamMuted(manager) ||
            manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }

    fun releaseAll() {
        synchronized(voiceLock) {
            voicePlayer?.release()
            voicePlayer = null
            voiceStreamId = -1
        }
        stemPlayers.forEach {
            it?.stop()
            it?.release()
        }
        stemPlayers.fill(null)
        soundPool.release()
        streamToPoolId.clear()
        loadedSounds.clear()
    }

    private fun isMusicStreamMuted(manager: AudioManager): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            manager.isStreamMute(AudioManager.STREAM_MUSIC)
}

internal class OneShotEngineImpl @Inject constructor(
    private val core: AudioEngineCore,
) : OneShotEngine {

    override fun loadOneShot(resName: String): Int =
        core.loadedSounds[resName] ?: loadNewSound(resName)

    override fun playOneShot(
        soundId: Int,
        volume: Float,
        pitch: Float,
        rampMs: Int,
        priority: Int,
    ): Int {
        if (soundId <= 0) return -1
        return startOneShot(soundId, volume, pitch, rampMs, priority)
    }

    override fun stopStream(streamId: Int) {
        core.streamToPoolId.remove(streamId)?.let { core.soundPool.stop(it) }
    }

    private fun loadNewSound(resName: String): Int {
        val resId = core.resIdFor(resName)
        if (resId == 0) return -1
        val soundId = core.soundPool.load(core.context, resId, 1)
        if (soundId == 0) return -1
        core.loadedSounds[resName] = soundId
        return soundId
    }

    private fun startOneShot(
        soundId: Int,
        volume: Float,
        pitch: Float,
        rampMs: Int,
        priority: Int,
    ): Int {
        val startVolume = if (rampMs > 0) MIN_AUDIBLE_VOLUME else volume
        val poolStreamId = core.soundPool.play(
            soundId,
            startVolume,
            startVolume,
            priority,
            0,
            pitch.coerceIn(MIN_PITCH, MAX_PITCH),
        )
        if (poolStreamId == 0) return -1
        val streamId = core.nextStreamId.getAndIncrement()
        core.streamToPoolId[streamId] = poolStreamId
        if (rampMs > 0) {
            core.rampStream(poolStreamId, volume, rampMs)
        }
        return streamId
    }
}

internal class VoiceEngineImpl @Inject constructor(
    private val core: AudioEngineCore,
) : VoiceEngine {

    override fun playVoice(resName: String, volume: Float, rampMs: Int): Int {
        val player = createVoicePlayer(resName) ?: return -1
        return startVoicePlayback(player, volume, rampMs)
    }

    override fun stopVoice(streamId: Int) {
        core.finishVoice(streamId)
    }

    override fun setOnVoiceEnd(listener: (streamId: Int) -> Unit) {
        core.voiceEndListener = listener
    }

    private fun createVoicePlayer(resName: String): MediaPlayer? {
        val resId = core.resIdFor(resName)
        if (resId == 0) return null
        val player = MediaPlayer.create(core.context, resId) ?: return null
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        return player
    }

    private fun startVoicePlayback(player: MediaPlayer, volume: Float, rampMs: Int): Int {
        val v = volume.coerceIn(0f, 1f)
        val startVolume = if (rampMs > 0) MIN_AUDIBLE_VOLUME else v
        player.setVolume(startVolume, startVolume)
        val streamId: Int
        synchronized(core.voiceLock) {
            core.voicePlayer?.let {
                it.stop()
                it.release()
            }
            streamId = core.nextStreamId.getAndIncrement()
            core.voicePlayer = player
            core.voiceStreamId = streamId
        }
        player.setOnCompletionListener {
            core.finishVoice(streamId)
        }
        player.start()
        if (rampMs > 0) {
            core.rampVoice(player, streamId, v, rampMs)
        }
        return streamId
    }
}

internal class MusicStemEngineImpl @Inject constructor(
    private val core: AudioEngineCore,
) : MusicStemEngine {

    override fun loadStem(stemIndex: Int): Int {
        if (stemIndex !in STEMS.indices) return -1
        return if (core.stemPlayers[stemIndex] != null) stemIndex else createStemPlayer(stemIndex)
    }

    override fun startStems() {
        core.stemPlayers.forEach { it?.start() }
    }

    override fun setStemVolume(stemIndex: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        core.stemPlayers.getOrNull(stemIndex)?.setVolume(v, v)
    }

    override fun stopStems() {
        core.stemPlayers.forEach { it?.pause() }
    }

    private fun createStemPlayer(stemIndex: Int): Int {
        val resId = core.resIdFor(STEMS[stemIndex])
        val player = if (resId == 0) null else MediaPlayer.create(core.context, resId)
        if (player == null) return -1
        player.isLooping = true
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        core.stemPlayers[stemIndex] = player
        return stemIndex
    }
}

/**
 * Android implementation: SoundPool (max 8 streams) for one-shots,
 * one MediaPlayer for the current voice, four looping MediaPlayers for the
 * §9 music stems (drums/bass/melody/sparkle).
 */
class AndroidAudioEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val core: AudioEngineCore = AudioEngineCore(context),
) : AudioEngine,
    OneShotEngine by OneShotEngineImpl(core),
    VoiceEngine by VoiceEngineImpl(core),
    MusicStemEngine by MusicStemEngineImpl(core) {

    override fun isMuted(): Boolean = core.isMuted()

    override fun release() = core.releaseAll()

    companion object {
        const val MAX_STREAMS = 8
    }
}
