package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

/**
 * Kid-safe comms for raid visits, per docs/contract.md §6.
 *
 * Exactly 12 preset phrases and 10 emotes. No free text, no voice, no
 * stranger contact. Ever. Phrases are sent as their index, emotes as their
 * drawable id, inside P2PChat payloads (§8).
 */
object RaidChatContent {

    const val KIND_PHRASE = "phrase"
    const val KIND_EMOTE = "emote"

    /** Frozen §6 list, in order. Indices are sent on the wire. */
    val PHRASES: List<String> = listOf(
        "Nice grab!",
        "My buddy! Give it back!",
        "Good game!",
        "That was sneaky!",
        "Wow, nice base!",
        "Rematch?",
        "You got lucky!",
        "I'll get you back!",
        "Cool buddy!",
        "Thanks for the visit!",
        "That was fun!",
        "Sneaky snout!",
    )

    /** Frozen §6 emote ids (vector drawables owned by the coordinator). */
    val EMOTES: List<String> = listOf(
        "emote_happy",
        "emote_sad",
        "emote_giggle",
        "emote_wow",
        "emote_angry_fun",
        "emote_heart",
        "emote_star",
        "emote_thumbsup",
        "emote_sleepy",
        "emote_party",
    )

    fun phraseText(index: Int): String? = PHRASES.getOrNull(index)

    fun isValidPhraseRef(refId: String): Boolean =
        refId.toIntOrNull()?.let { it in PHRASES.indices } == true

    fun isValidEmoteRef(refId: String): Boolean = refId in EMOTES
}
