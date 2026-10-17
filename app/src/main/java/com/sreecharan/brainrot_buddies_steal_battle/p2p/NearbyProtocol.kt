package com.sreecharan.brainrot_buddies_steal_battle.p2p

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Nearby P2P wire protocol, frozen per docs/contract.md §8.
 *
 * Service ID: com.sreecharan.brainrot_buddies_steal_battle.NEARBY_V1
 * Strategy: P2P_STAR. Endpoint name = player's base name.
 * Payloads are kotlinx.serialization JSON. Every payload carries "v": 1.
 * All payloads stay tiny (<4 KB); snapshots stay under 2 KB.
 *
 * Transport framing: each payload is wrapped in a [PayloadEnvelope] that
 * names its type. The wrapped payload schemas match §8 exactly.
 */
object NearbyProtocol {
    const val SERVICE_ID = "com.sreecharan.brainrot_buddies_steal_battle.NEARBY_V1"
    const val PROTOCOL_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 4096
    const val MAX_SNAPSHOT_BYTES = 2048

    const val TYPE_HELLO = "hello"
    const val TYPE_SNAPSHOT = "snapshot"
    const val TYPE_RAID_REQUEST = "raid_request"
    const val TYPE_RAID_RESULT = "raid_result"
    const val TYPE_CHAT = "chat"

    /**
     * Shadow-heist sync notice. §8 requires that "on next P2P connection
     * with that friend, attacker sends the pending RaidRecord" so the victim
     * gets the replay card, revenge option, and consolation. This is the wire
     * form of that pending record; every payload still carries "v": 1.
     */
    const val TYPE_SHADOW_NOTICE = "shadow_notice"

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }

    fun encode(payload: P2pPayload): ByteArray {
        val type =
            when (payload) {
                is Hello -> TYPE_HELLO
                is BaseSnapshot -> TYPE_SNAPSHOT
                is RaidRequest -> TYPE_RAID_REQUEST
                is RaidResult -> TYPE_RAID_RESULT
                is P2PChat -> TYPE_CHAT
                is ShadowNotice -> TYPE_SHADOW_NOTICE
            }
        val body =
            when (payload) {
                is Hello -> json.encodeToString(Hello.serializer(), payload)
                is BaseSnapshot -> json.encodeToString(BaseSnapshot.serializer(), payload)
                is RaidRequest -> json.encodeToString(RaidRequest.serializer(), payload)
                is RaidResult -> json.encodeToString(RaidResult.serializer(), payload)
                is P2PChat -> json.encodeToString(P2PChat.serializer(), payload)
                is ShadowNotice -> json.encodeToString(ShadowNotice.serializer(), payload)
            }
        val envelope = PayloadEnvelope(type = type, body = body)
        return json.encodeToString(PayloadEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
    }

    /**
     * Decodes one envelope. Returns null for oversized, malformed, unknown,
     * or version-mismatched payloads. Never throws.
     */
    fun decode(bytes: ByteArray): P2pPayload? =
        if (bytes.size > MAX_PAYLOAD_BYTES) {
            null
        } else {
            decodeEnvelope(bytes)
        }

    private fun decodeEnvelope(bytes: ByteArray): P2pPayload? {
        val envelope =
            runCatching {
                json.decodeFromString(PayloadEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
            }.getOrNull()
        return envelope?.let { env ->
            runCatching { decodeBody(env) }
                .getOrNull()
                ?.takeIf { it.v == PROTOCOL_VERSION && snapshotWithinLimit(env, it) }
        }
    }

    private fun decodeBody(envelope: PayloadEnvelope): P2pPayload? =
        when (envelope.type) {
            TYPE_HELLO -> json.decodeFromString(Hello.serializer(), envelope.body)
            TYPE_SNAPSHOT -> json.decodeFromString(BaseSnapshot.serializer(), envelope.body)
            TYPE_RAID_REQUEST -> json.decodeFromString(RaidRequest.serializer(), envelope.body)
            TYPE_RAID_RESULT -> json.decodeFromString(RaidResult.serializer(), envelope.body)
            TYPE_CHAT -> json.decodeFromString(P2PChat.serializer(), envelope.body)
            TYPE_SHADOW_NOTICE -> json.decodeFromString(ShadowNotice.serializer(), envelope.body)
            else -> null
        }

    private fun snapshotWithinLimit(
        envelope: PayloadEnvelope,
        payload: P2pPayload,
    ): Boolean {
        if (payload !is BaseSnapshot) return true
        return envelope.body.toByteArray(Charsets.UTF_8).size <= MAX_SNAPSHOT_BYTES
    }
}

/** Marker for every payload on the wire. Each concrete payload carries "v". */
sealed interface P2pPayload {
    val v: Int
}

/** Transport envelope: names the payload type, carries the payload JSON. */
@Serializable
data class PayloadEnvelope(
    val type: String,
    val body: String,
)

/** §8: pairing handshake. Sent after both sides tap "Match!". */
@Serializable
data class Hello(
    override val v: Int = 1,
    val guestId: String,
    val baseName: String,
    val code: String,
) : P2pPayload

/** §8: one occupied base slot inside a snapshot. */
@Serializable
data class SnapshotSlot(
    val slot: Int,
    val buddyId: String,
    val level: Int,
)

/** §8: a friend's saved base, exchanged over Nearby when together. */
@Serializable
data class BaseSnapshot(
    override val v: Int = 1,
    val guestId: String,
    val baseName: String,
    val exportedAt: Long,
    val shieldUntil: Long,
    val coinsPerSec: Int,
    val slots: List<SnapshotSlot>,
) : P2pPayload

/** §8: attacker asks to steal the buddy on [slot] of the victim's base. */
@Serializable
data class RaidRequest(
    override val v: Int = 1,
    val raidId: String,
    val attackerGuestId: String,
    val attackerBaseName: String,
    val slot: Int,
) : P2pPayload

/**
 * §8: victim's answer. [reason] is one of
 * "shielded" | "cooldown" | "empty" | "already_raided".
 */
@Serializable
data class RaidResult(
    override val v: Int = 1,
    val raidId: String,
    val accepted: Boolean,
    val buddyId: String? = null,
    val buddyLevel: Int = 1,
    val reason: String? = null,
) : P2pPayload

/**
 * §8: kid-safe comms during a visit. [kind] is "phrase" or "emote";
 * [refId] is the phrase index or the emote id. No free text, ever.
 */
@Serializable
data class P2PChat(
    override val v: Int = 1,
    val kind: String,
    val refId: String,
) : P2pPayload

/**
 * Shadow-heist sync: the attacker replays a pending shadow raid record to
 * the victim on their next P2P connection (§8 "shadow heist" semantics).
 */
@Serializable
data class ShadowNotice(
    override val v: Int = 1,
    val raidId: String,
    val buddyId: String,
    val buddyLevel: Int,
    val attackerGuestId: String,
    val attackerBaseName: String,
    val createdAt: Long,
) : P2pPayload
