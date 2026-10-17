package com.sreecharan.brainrot_buddies_steal_battle.core.audio

import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract §9 rules as executable specs: priority ladder, 8-sound cap,
 * 6 dB ducking with 0.8 s ease-back, 5-8% jitter on utility SFX only,
 * >= 100 ms ramps on stings/voices, channel math, mute/DND honor, stem
 * layering, and no audio before the first tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundManagerTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var engine: FakeAudioEngine
    private lateinit var volumes: FakeGamePrefs
    private lateinit var manager: SoundManager

    @BeforeEach
    fun setUp() {
        engine = FakeAudioEngine()
        volumes = FakeGamePrefs()
        manager = SoundManager(engine, volumes, testDispatcher)
        testDispatcher.scheduler.runCurrent()
    }

    // ------------------------------------------------------------------
    // First-tap gate
    // ------------------------------------------------------------------

    @Test
    fun `no audio before the first tap`() =
        runTest(testDispatcher) {
            manager.playClaimChime()
            manager.playButton()
            manager.playVoice("voice_frogboot")
            manager.startMusic()
            runCurrent()

            assertTrue(engine.oneShotCalls.isEmpty(), "one-shots must stay silent before unlock")
            assertTrue(engine.voiceCalls.isEmpty(), "voices must stay silent before unlock")
            assertEquals(0, engine.stemsStarted, "music must not start before unlock")
        }

    @Test
    fun `audio plays after unlock`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            manager.playButton()
            runCurrent()

            assertEquals(1, engine.oneShotCalls.size)
        }

    // ------------------------------------------------------------------
    // Priority + 8-sound cap
    // ------------------------------------------------------------------

    @Test
    fun `sting evicts oldest ambient when cap is full`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            repeat(8) { manager.playButton() }
            runCurrent()
            assertEquals(8, engine.oneShotCalls.size)

            manager.playRaidBell()
            runCurrent()

            assertEquals(9, engine.oneShotCalls.size, "the sting must play")
            assertEquals(1, engine.stoppedStreams.size, "one ambient must be evicted")
            val evictedStream = engine.stoppedStreams.single()
            val evictedCall = engine.oneShotCalls.first { it.streamId == evictedStream }
            assertEquals(SoundKind.AMBIENT.priority, evictedCall.priority)
        }

    @Test
    fun `voice is never evicted for a lower kind`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            repeat(8) { manager.playVoice("voice_frogboot") }
            runCurrent()
            assertEquals(8, engine.voiceCalls.size)

            manager.playButton()
            runCurrent()

            assertEquals(0, engine.oneShotCalls.size, "button must be dropped, voices kept")
            assertTrue(engine.stoppedVoices.isEmpty(), "no voice may be stopped for an ambient")
        }

    @Test
    fun `new voice evicts oldest voice when all streams are voices`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            repeat(8) { manager.playVoice("voice_frogboot") }
            runCurrent()
            val firstVoiceStream = engine.voiceCalls.first().streamId

            manager.playVoice("voice_benny")
            runCurrent()

            assertEquals(9, engine.voiceCalls.size, "the new voice must play")
            assertTrue(engine.stoppedVoices.contains(firstVoiceStream), "oldest voice evicted")
        }

    // ------------------------------------------------------------------
    // Ducking: -6 dB under voices, 0.8 s ease-back
    // ------------------------------------------------------------------

    @Test
    fun `music ducks 6dB under voice and eases back over 0_8s`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            manager.startMusic()
            runCurrent()
            val calmMelody = engine.stemVolumes[2] ?: 0f
            assertTrue(calmMelody > 0f, "music should be playing")

            manager.playVoice("voice_frogboot")
            advanceTimeBy(300)

            val duckedMelody = engine.stemVolumes[2] ?: 0f
            assertEquals(calmMelody * SoundManager.DUCK_FACTOR, duckedMelody, 0.001f)

            engine.finishVoice(engine.voiceCalls.last().streamId)
            advanceTimeBy(400)
            val midEase = engine.stemVolumes[2] ?: 0f
            assertTrue(midEase > duckedMelody, "ease-back must be gradual, not instant")
            assertTrue(midEase < calmMelody, "ease-back must not be done at 400ms")

            advanceTimeBy(500)
            assertEquals(calmMelody, engine.stemVolumes[2] ?: 0f, 0.001f)
        }

    // ------------------------------------------------------------------
    // Jitter: 5-8% on utility SFX only, never voices or jingles
    // ------------------------------------------------------------------

    @Test
    fun `utility sfx get 5 to 8 percent pitch and volume jitter`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            repeat(40) { manager.playButton() }
            runCurrent()

            assertEquals(40, engine.oneShotCalls.size)
            engine.oneShotCalls.forEach { call ->
                assertTrue(call.pitch in 0.92f..1.08f, "pitch ${call.pitch} outside jitter band")
                assertTrue(call.volume in 0.55f..0.65f, "volume ${call.volume} outside jitter band")
            }
            val distinctPitches =
                engine.oneShotCalls
                    .map { it.pitch }
                    .toSet()
                    .size
            assertTrue(distinctPitches > 1, "jitter must actually vary between triggers")
        }

    @Test
    fun `jingles and voices are never jittered`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            repeat(10) { manager.playClaimChime() }
            // Level 6 -> pitch 1.6f, the smallest level that clears the 1.5f
            // upgrade skip below (level-based pitch, not jitter).
            manager.playUpgrade(6)
            runCurrent()

            engine.oneShotCalls.forEach { call ->
                if (call.pitch > 1.5f) return@forEach // upgrade pitch is level-based, tested separately
                assertEquals(1f, call.pitch, 0.0001f, "jingle pitch must be exact")
            }

            manager.playVoice("voice_frogboot")
            runCurrent()
            assertEquals(1f, engine.voiceCalls.single().volume, 0.0001f)
            assertTrue(engine.voiceCalls.single().rampMs >= 100, "voice must ramp in >= 100ms")
        }

    @Test
    fun `every sting ramps in at least 100ms`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            manager.playRaidBell()
            manager.playGiftOpen()
            manager.playVaultCollect()
            runCurrent()

            assertEquals(3, engine.oneShotCalls.size)
            engine.oneShotCalls.forEach { call ->
                assertTrue(call.rampMs >= 100, "sting ramp ${call.rampMs}ms is a startle risk")
            }
        }

    // ------------------------------------------------------------------
    // Channels, mute, missing assets
    // ------------------------------------------------------------------

    @Test
    fun `channel volumes scale 0 to 100 linearly`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            volumes.musicFlow.value = 50
            volumes.sfxFlow.value = 0
            runCurrent()

            manager.startMusic()
            runCurrent()
            assertEquals(0.9f * 0.5f, engine.stemVolumes[2] ?: 0f, 0.001f)

            manager.playButton()
            runCurrent()
            assertTrue(engine.oneShotCalls.isEmpty(), "zeroed channel must not consume the cap")
        }

    @Test
    fun `hardware mute suppresses everything`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            engine.muted = true

            manager.playButton()
            manager.playVoice("voice_frogboot")
            runCurrent()

            assertTrue(engine.oneShotCalls.isEmpty())
            assertTrue(engine.voiceCalls.isEmpty())
        }

    @Test
    fun `missing raw assets are silent no-ops`() =
        runTest(testDispatcher) {
            engine.missing.add("sfx_button")
            manager.unlockAudio()

            manager.playButton()
            runCurrent()

            assertTrue(engine.oneShotCalls.isEmpty(), "pre-merge builds must not crash on missing audio")
        }

    // ------------------------------------------------------------------
    // Stem layering
    // ------------------------------------------------------------------

    @Test
    fun `raid mode plays full band, calm otherwise`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            manager.startMusic()
            runCurrent()
            assertEquals(0f, engine.stemVolumes[0] ?: -1f, 0.001f, "drums rest when calm")

            manager.setRaidMode(true)
            runCurrent()
            for (i in 0..3) {
                assertEquals(1f, engine.stemVolumes[i] ?: 0f, 0.001f, "full band in raid window")
            }

            manager.setRaidMode(false)
            runCurrent()
            assertEquals(0f, engine.stemVolumes[0] ?: -1f, 0.001f, "back to calm layer")
        }

    @Test
    fun `wind-down strips to calm layer after 20 minutes`() =
        runTest(testDispatcher) {
            manager.unlockAudio()
            manager.setRaidMode(true)
            manager.startMusic()
            runCurrent()
            assertEquals(1f, engine.stemVolumes[0] ?: 0f, 0.001f)

            advanceTimeBy(20 * 60 * 1000L + 1_000L)
            runCurrent()

            assertEquals(0f, engine.stemVolumes[0] ?: -1f, 0.001f, "drums stripped after wind-down")
            assertTrue((engine.stemVolumes[2] ?: 0f) > 0f, "melody keeps playing")
        }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeGamePrefs : GamePrefs {
        val musicFlow = MutableStateFlow(100)
        val sfxFlow = MutableStateFlow(100)
        val voiceFlow = MutableStateFlow(100)
        val bitesFlow = MutableStateFlow(0)
        val albumBonusFlow = MutableStateFlow<Set<String>>(emptySet())

        override val guestId: Flow<String?> get() = MutableStateFlow(null)
        override val baseName: Flow<String> get() = MutableStateFlow("")
        override val musicVolume: Flow<Int> get() = musicFlow
        override val sfxVolume: Flow<Int> get() = sfxFlow
        override val voiceVolume: Flow<Int> get() = voiceFlow
        override val hapticsEnabled: Flow<Boolean> get() = MutableStateFlow(true)
        override val onboardingDay: Flow<Int> get() = MutableStateFlow(1)
        override val tutorialDone: Flow<Boolean> get() = MutableStateFlow(false)
        override val adsRemoved: Flow<Boolean> get() = MutableStateFlow(false)
        override val posterPath: Flow<String> get() = MutableStateFlow("")
        override val lastRaidWindow: Flow<String> get() = MutableStateFlow("")
        override val raidsUsed: Flow<Int> get() = MutableStateFlow(0)
        override val bennyHintsEnabled: Flow<Boolean> get() = MutableStateFlow(true)
        override val usedCodes: Flow<Set<String>> get() = MutableStateFlow(emptySet())
        override val lastWeeklyGiftDate: Flow<String> get() = MutableStateFlow("")
        override val buddyBites: Flow<Int> get() = bitesFlow
        override val claimedAlbumBonuses: Flow<Set<String>> get() = albumBonusFlow

        override suspend fun ensureGuestId(): String = "fake-guest"

        override suspend fun setBaseName(name: String) = Unit

        override suspend fun setMusicVolume(volume: Int) {
            musicFlow.value = volume
        }

        override suspend fun setSfxVolume(volume: Int) {
            sfxFlow.value = volume
        }

        override suspend fun setVoiceVolume(volume: Int) {
            voiceFlow.value = volume
        }

        override suspend fun setHapticsEnabled(enabled: Boolean) = Unit

        override suspend fun setOnboardingDay(day: Int) = Unit

        override suspend fun setTutorialDone(done: Boolean) = Unit

        override suspend fun setAdsRemoved(removed: Boolean) = Unit

        override suspend fun setPosterPath(path: String) = Unit

        override suspend fun setLastRaidWindow(bucket: String) = Unit

        override suspend fun setRaidsUsed(count: Int) = Unit

        override suspend fun setBennyHintsEnabled(enabled: Boolean) = Unit

        override suspend fun addUsedCode(code: String) = Unit

        override suspend fun setLastWeeklyGiftDate(date: String) = Unit

        override suspend fun setBuddyBites(count: Int) {
            bitesFlow.value = count
        }

        override suspend fun addClaimedAlbumBonus(tier: String) {
            albumBonusFlow.value = albumBonusFlow.value + tier
        }

        override suspend fun clearAll() = Unit
    }

    private class FakeAudioEngine : AudioEngine {
        var muted = false
        val missing = mutableSetOf<String>()

        data class OneShotCall(
            val soundId: Int,
            val volume: Float,
            val pitch: Float,
            val rampMs: Int,
            val priority: Int,
            val streamId: Int,
        )

        data class VoiceCall(
            val resName: String,
            val volume: Float,
            val rampMs: Int,
            val streamId: Int,
        )

        val oneShotCalls = mutableListOf<OneShotCall>()
        val voiceCalls = mutableListOf<VoiceCall>()
        val stoppedStreams = mutableListOf<Int>()
        val stoppedVoices = mutableListOf<Int>()
        val stemVolumes = mutableMapOf<Int, Float>()
        var stemsStarted = 0

        private var voiceEnd: (Int) -> Unit = {}
        private var nextId = 1

        // Stable positive sound ids like a real SoundPool: non-positive ids
        // mean "load failed" to SoundPlaybackCore, and String.hashCode() is
        // negative for several of our asset names (e.g. sfx_button).
        private val loadedIds = mutableMapOf<String, Int>()

        override fun loadOneShot(resName: String): Int {
            if (resName in missing) return -1
            return loadedIds.getOrPut(resName) { loadedIds.size + 1 }
        }

        override fun playOneShot(
            soundId: Int,
            volume: Float,
            pitch: Float,
            rampMs: Int,
            priority: Int,
        ): Int {
            if (soundId <= 0) return -1
            val streamId = nextId++
            oneShotCalls.add(OneShotCall(soundId, volume, pitch, rampMs, priority, streamId))
            return streamId
        }

        override fun stopStream(streamId: Int) {
            stoppedStreams.add(streamId)
        }

        override fun playVoice(
            resName: String,
            volume: Float,
            rampMs: Int,
        ): Int {
            if (resName in missing) return -1
            val streamId = nextId++
            voiceCalls.add(VoiceCall(resName, volume, rampMs, streamId))
            return streamId
        }

        override fun stopVoice(streamId: Int) {
            stoppedVoices.add(streamId)
        }

        override fun loadStem(stemIndex: Int): Int = stemIndex

        override fun startStems() {
            stemsStarted++
        }

        override fun setStemVolume(
            stemIndex: Int,
            volume: Float,
        ) {
            stemVolumes[stemIndex] = volume
        }

        override fun stopStems() {
            // no-op: test double has no stems to stop
        }

        override fun isMuted(): Boolean = muted

        override fun setOnVoiceEnd(listener: (Int) -> Unit) {
            voiceEnd = listener
        }

        override fun release() {
            // no-op: test double holds no native resources
        }

        fun finishVoice(streamId: Int) = voiceEnd(streamId)
    }
}
