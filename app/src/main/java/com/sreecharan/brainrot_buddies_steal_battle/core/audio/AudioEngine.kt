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
 */
interface AudioEngine {

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

    /** Plays a voice line (MediaPlayer); returns a stream id, or -1. */
    fun playVoice(resName: String, volume: Float, rampMs: Int): Int

    fun stopVoice(streamId: Int)

    /** Loads one music stem by index into [AudioAssets.STEMS]; -1 if missing. */
    fun loadStem(stemIndex: Int): Int

    /** Starts all loaded stems looping, as close to in-sync as the platform
     *  allows. */
    fun startStems()

    fun setStemVolume(stemIndex: Int, volume: Float)

    fun stopStems()

    /** True when the device is muted or in Do Not Disturb (contract §9:
     *  honor hardware mute + DND). */
    fun isMuted(): Boolean

    /** Fired on the engine's thread when a voice stream finishes (or is
     *  stopped). SoundPool one-shots have no completion callback; their
     *  ducking windows are time-boxed by [SoundManager] instead. */
    fun setOnVoiceEnd(listener: (streamId: Int) -> Unit)

    fun release()
}

/**
 * Android implementation: SoundPool (max 8 streams) for one-shots,
 * one MediaPlayer for the current voice, four looping MediaPlayers for the
 * §9 music stems (drums/bass/melody/sparkle).
 */
class AndroidAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioEngine {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val audioManager: AudioManager? =
        context.getSystemService(AudioManager::class.java)

    private val loadedSounds = ConcurrentHashMap<String, Int>()
    private val nextStreamId = AtomicInteger(1)
    private val streamToPoolId = ConcurrentHashMap<Int, Int>()

    private val voiceLock = Any()
    private var voicePlayer: MediaPlayer? = null
    private var voiceStreamId: Int = -1
    private var voiceEndListener: (Int) -> Unit = {}

    private val stemPlayers = arrayOfNulls<MediaPlayer>(STEMS.size)

    override fun loadOneShot(resName: String): Int {
        loadedSounds[resName]?.let { return it }
        val resId = resIdFor(resName)
        if (resId == 0) return -1
        val soundId = soundPool.load(context, resId, 1)
        if (soundId == 0) return -1
        loadedSounds[resName] = soundId
        return soundId
    }

    override fun playOneShot(
        soundId: Int,
        volume: Float,
        pitch: Float,
        rampMs: Int,
        priority: Int,
    ): Int {
        if (soundId <= 0) return -1
        val startVolume = if (rampMs > 0) MIN_AUDIBLE_VOLUME else volume
        val poolStreamId = soundPool.play(
            soundId,
            startVolume,
            startVolume,
            priority,
            0,
            pitch.coerceIn(MIN_PITCH, MAX_PITCH),
        )
        if (poolStreamId == 0) return -1
        val streamId = nextStreamId.getAndIncrement()
        streamToPoolId[streamId] = poolStreamId
        if (rampMs > 0) {
            rampStream(poolStreamId, volume, rampMs)
        }
        return streamId
    }

    override fun stopStream(streamId: Int) {
        streamToPoolId.remove(streamId)?.let { soundPool.stop(it) }
    }

    override fun playVoice(resName: String, volume: Float, rampMs: Int): Int {
        val resId = resIdFor(resName)
        if (resId == 0) return -1
        val player = MediaPlayer.create(context, resId) ?: return -1
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val v = volume.coerceIn(0f, 1f)
        val startVolume = if (rampMs > 0) MIN_AUDIBLE_VOLUME else v
        player.setVolume(startVolume, startVolume)
        val streamId: Int
        synchronized(voiceLock) {
            voicePlayer?.let {
                it.stop()
                it.release()
            }
            streamId = nextStreamId.getAndIncrement()
            voicePlayer = player
            voiceStreamId = streamId
        }
        player.setOnCompletionListener {
            finishVoice(streamId)
        }
        player.start()
        if (rampMs > 0) {
            rampVoice(player, streamId, v, rampMs)
        }
        return streamId
    }

    override fun stopVoice(streamId: Int) {
        finishVoice(streamId)
    }

    override fun loadStem(stemIndex: Int): Int {
        if (stemIndex !in STEMS.indices) return -1
        if (stemPlayers[stemIndex] != null) return stemIndex
        val resId = resIdFor(STEMS[stemIndex])
        if (resId == 0) return -1
        val player = MediaPlayer.create(context, resId) ?: return -1
        player.isLooping = true
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        stemPlayers[stemIndex] = player
        return stemIndex
    }

    override fun startStems() {
        stemPlayers.forEach { it?.start() }
    }

    override fun setStemVolume(stemIndex: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        stemPlayers.getOrNull(stemIndex)?.setVolume(v, v)
    }

    override fun stopStems() {
        stemPlayers.forEach { it?.pause() }
    }

    override fun isMuted(): Boolean {
        val manager = audioManager ?: return false
        if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (manager.isStreamMute(AudioManager.STREAM_MUSIC)) return true
        }
        return manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }

    override fun setOnVoiceEnd(listener: (streamId: Int) -> Unit) {
        voiceEndListener = listener
    }

    override fun release() {
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

    private fun finishVoice(streamId: Int) {
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

    private fun resIdFor(resName: String): Int =
        context.resources.getIdentifier(resName, "raw", context.packageName)

    /**
     * Best-effort fade-in on a daemon thread. SoundPool/MediaPlayer have no
     * native fade, so we step the per-stream volume; steps are small enough
     * to be click-free and the total is never shorter than [rampMs].
     */
    private fun rampStream(poolStreamId: Int, targetVolume: Float, rampMs: Int) {
        fadeThread(rampMs) { fraction ->
            val v = (targetVolume * fraction).coerceIn(0f, 1f)
            soundPool.setVolume(poolStreamId, v, v)
        }
    }

    private fun rampVoice(player: MediaPlayer, streamId: Int, targetVolume: Float, rampMs: Int) {
        fadeThread(rampMs) { fraction ->
            synchronized(voiceLock) {
                if (voiceStreamId != streamId) return@fadeThread
            }
            val v = (targetVolume * fraction).coerceIn(0f, 1f)
            player.setVolume(v, v)
        }
    }

    private fun fadeThread(rampMs: Int, apply: (Float) -> Unit) {
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

    companion object {
        const val MAX_STREAMS = 8
        private const val RAMP_STEP_MS = 25L
        private const val MIN_AUDIBLE_VOLUME = 0.01f
        private const val MIN_PITCH = 0.5f
        private const val MAX_PITCH = 2.0f
    }
}
