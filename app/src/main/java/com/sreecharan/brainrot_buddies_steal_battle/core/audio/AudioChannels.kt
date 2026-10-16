package com.sreecharan.brainrot_buddies_steal_battle.core.audio

/**
 * The [SoundManager]'s public surface, split into focused seams so no
 * single interface exceeds the complexity budget. [SoundManager] composes
 * them all and stays the type the UI injects.
 */

/** One-shot SFX hooks (§9). */
interface SfxPlayer {
    fun playClaimChime()
    fun playCoinTick(incomeRate: Float = 1f)
    fun playStealSuccess()
    fun playStealFail()
    fun playUpgrade(level: Int)
    fun playShieldBreak()
    fun playButton()
    fun playVaultCollect()
    fun playGiftOpen()
    fun playRaidBell()
}

/** Voice-line playback. Never jittered, always ramped. */
interface VoicePlayer {
    /** Plays `voice_<buddyId>` (or `voice_benny` etc.). */
    fun playVoice(voiceResName: String)
}

/** Music stem control (§9 toy-pop loop). */
interface MusicController {
    /** Starts the §9 toy-pop loop. Full band only in raid/event mode. */
    fun startMusic()
    fun stopMusic()

    /** Contract §9: full band during raid windows. */
    fun setRaidMode(active: Boolean)

    /** Zara's Saturday event: full band, event layer. Same stems, event-only. */
    fun setEventMode(active: Boolean)

    /** Any real interaction resets the 20-minute wind-down clock. */
    fun pokeActivity()
}

/** Audio lifecycle: first-tap unlock and teardown. */
interface AudioLifecycle {
    /** Call on the first user tap anywhere. Before this, everything is silent. */
    fun unlockAudio()
    fun isUnlocked(): Boolean
    fun release()
}
