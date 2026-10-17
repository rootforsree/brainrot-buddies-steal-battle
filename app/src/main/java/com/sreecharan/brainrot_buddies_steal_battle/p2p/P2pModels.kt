package com.sreecharan.brainrot_buddies_steal_battle.p2p

import androidx.compose.runtime.Immutable

/**
 * UI-agnostic P2P models shared by the Friends and Raid screens.
 *
 * One nearby endpoint found during discovery. The name is the player's base name.
 */
@Immutable
data class DiscoveredPeer(
    val endpointId: String,
    val baseName: String,
)

/**
 * Pairing ceremony phases, per docs/contract.md §8:
 * discover -> select base name -> auth token on both screens ->
 * mutual "Match!" -> Hello exchange -> code verify -> saved as Friend.
 */
sealed interface PairingPhase {
    data object Idle : PairingPhase

    data object Discovering : PairingPhase

    data class AwaitingToken(
        val peerBaseName: String,
        val weInitiated: Boolean,
    ) : PairingPhase

    data class ConfirmToken(
        val peerBaseName: String,
        val authToken: String,
    ) : PairingPhase

    data class VerifyCode(
        val peerBaseName: String,
        val peerCode: String,
    ) : PairingPhase

    data class Paired(
        val baseName: String,
    ) : PairingPhase

    data class Failed(
        val message: String,
    ) : PairingPhase
}

/** Connection lifecycle events for visit/pairing orchestration. */
sealed interface ConnectionEvent {
    data class PeerFound(
        val peer: DiscoveredPeer,
    ) : ConnectionEvent

    data class PeerLost(
        val endpointId: String,
    ) : ConnectionEvent

    data class TokenReady(
        val endpointId: String,
        val baseName: String,
        val authToken: String,
        val incoming: Boolean,
    ) : ConnectionEvent

    data class Connected(
        val endpointId: String,
        val baseName: String,
    ) : ConnectionEvent

    data class ConnectFailed(
        val endpointId: String,
    ) : ConnectionEvent

    data class Disconnected(
        val endpointId: String,
    ) : ConnectionEvent
}

/** One decoded inbound payload with its sender. */
data class InboundPayload(
    val endpointId: String,
    val payload: P2pPayload,
)

/** Hosting mode controls whether incoming connections auto-accept. */
enum class HostMode {
    /** Pairing ceremony: wait for the "Match!" tap before accepting. */
    PAIRING,

    /** Visit mode: auto-accept, then verify the Hello guestId is a friend. */
    VISIT,
}
