package com.sreecharan.brainrot_buddies_steal_battle.core.audio

/**
 * The three independent volume channels from contract §9. Each has its own
 * 0-100 slider persisted in DataStore (see `GamePrefs`).
 */
enum class AudioChannel {
    MUSIC,
    SOUNDS,
    VOICES,
}

/**
 * Priority ladder from contract §9: voice > event stings > reward SFX >
 * ambient ticks > music bed. Higher [priority] wins when the 8-sound cap
 * forces an eviction.
 */
enum class SoundKind(
    val priority: Int,
) {
    MUSIC(PRIORITY_MUSIC),
    AMBIENT(PRIORITY_AMBIENT),
    REWARD(PRIORITY_REWARD),
    STING(PRIORITY_STING),
    VOICE(PRIORITY_VOICE),
}

private const val PRIORITY_MUSIC = 0
private const val PRIORITY_AMBIENT = 1
private const val PRIORITY_REWARD = 2
private const val PRIORITY_STING = 3
private const val PRIORITY_VOICE = 4

/**
 * One playable sound: its frozen `res/raw` name (without extension), its
 * [SoundKind] (drives priority, ducking, jitter and ramp rules) and the
 * [AudioChannel] whose volume scales it.
 */
data class SoundSpec(
    val resName: String,
    val kind: SoundKind,
    val channel: AudioChannel,
    val baseVolume: Float = 1f,
)

/**
 * Frozen `res/raw` asset names from contract §9. The coordinator lands the
 * actual files before merge; [SoundManager] treats missing files as silent
 * no-ops so the app runs before the audio drop.
 */
object AudioAssets {
    const val SFX_CLAIM_CHIME = "sfx_claim_chime"
    const val SFX_COIN_TICK = "sfx_coin_tick"
    const val SFX_STEAL_SUCCESS = "sfx_steal_success"
    const val SFX_STEAL_FAIL = "sfx_steal_fail"
    const val SFX_UPGRADE = "sfx_upgrade"
    const val SFX_SHIELD_BREAK = "sfx_shield_break"
    const val SFX_BUTTON = "sfx_button"
    const val SFX_VAULT_COLLECT = "sfx_vault_collect"
    const val SFX_GIFT_OPEN = "sfx_gift_open"
    const val SFX_RAID_BELL = "sfx_raid_bell"

    const val STEM_DRUMS = "music_drums"
    const val STEM_BASS = "music_bass"
    const val STEM_MELODY = "music_melody"
    const val STEM_SPARKLE = "music_sparkle"

    /** Stem order is frozen: drums, bass, melody, sparkle. */
    val STEMS: List<String> = listOf(STEM_DRUMS, STEM_BASS, STEM_MELODY, STEM_SPARKLE)

    const val VOICE_PREFIX = "voice_"

    fun voiceResName(buddyId: String): String = "$VOICE_PREFIX$buddyId"
}
