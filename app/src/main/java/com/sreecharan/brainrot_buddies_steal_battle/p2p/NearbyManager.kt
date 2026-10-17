package com.sreecharan.brainrot_buddies_steal_battle.p2p

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nearby Connections transport for friend-only P2P, per docs/contract.md §8.
 *
 * - Service ID: com.sreecharan.brainrot_buddies_steal_battle.NEARBY_V1
 * - Strategy P2P_STAR, endpoint name = player's base name.
 * - API 31+ only: [p2pAvailable] is false below Android 12 and every start
 *   call is a no-op there. Location permission is never requested.
 * - Bluetooth permissions are requested in-context by the Friends screen,
 *   never at launch.
 *
 * This class owns the transport only. Pairing ceremony, raid orchestration,
 * and victim-side raid validation live in the ViewModels and
 * [LiveRaidResponder]. Payloads are dispatched by type: raid payloads go to
 * the responder, everything else flows to [inbound] for the ViewModels.
 */
@Singleton
class NearbyManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** P2P features require API 31+. Below that the Friends UI shows the upgrade message. */
        val p2pAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }

        private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
        val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

        private val _inbound = MutableSharedFlow<InboundPayload>(extraBufferCapacity = 64)
        val inbound: SharedFlow<InboundPayload> = _inbound.asSharedFlow()

        private var myBaseName: String = ""
        private var myHello: Hello? = null
        private var hostMode: HostMode? = null

        private data class TokenInfo(
            val baseName: String,
            val token: String,
            val incoming: Boolean,
        )

        private val pendingTokens = mutableMapOf<String, TokenInfo>()
        private val connectedNames = mutableMapOf<String, String>()

        private val endpointDiscoveryCallback =
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(
                    endpointId: String,
                    info: DiscoveredEndpointInfo,
                ) {
                    _connectionEvents.tryEmit(
                        ConnectionEvent.PeerFound(DiscoveredPeer(endpointId, info.endpointName)),
                    )
                }

                override fun onEndpointLost(endpointId: String) {
                    _connectionEvents.tryEmit(ConnectionEvent.PeerLost(endpointId))
                }
            }

        private val connectionLifecycleCallback =
            object : ConnectionLifecycleCallback() {
                @Suppress("DEPRECATION")
                override fun onConnectionInitiated(
                    endpointId: String,
                    info: ConnectionInfo,
                ) {
                    val baseName = info.endpointName
                    pendingTokens[endpointId] = TokenInfo(baseName, info.authenticationToken, incoming = true)
                    connectedNames[endpointId] = baseName
                    if (hostMode == HostMode.VISIT) {
                        // Visit mode: both kids are together for a raid; accept right
                        // away. The Hello guestId is verified against the friend list
                        // by the RaidVisitViewModel; strangers are disconnected.
                        acceptConnection(endpointId)
                    }
                    _connectionEvents.tryEmit(
                        ConnectionEvent.TokenReady(endpointId, baseName, info.authenticationToken, incoming = true),
                    )
                }

                override fun onConnectionResult(
                    endpointId: String,
                    result: ConnectionResolution,
                ) {
                    pendingTokens.remove(endpointId)
                    if (result.status.isSuccess) {
                        val name = connectedNames.getOrPut(endpointId) { "" }
                        _connectionEvents.tryEmit(ConnectionEvent.Connected(endpointId, name))
                        // Identify ourselves on every new connection.
                        myHello?.let { sendPayload(endpointId, it) }
                    } else {
                        _connectionEvents.tryEmit(ConnectionEvent.ConnectFailed(endpointId))
                    }
                }

                override fun onDisconnected(endpointId: String) {
                    connectedNames.remove(endpointId)
                    pendingTokens.remove(endpointId)
                    _connectionEvents.tryEmit(ConnectionEvent.Disconnected(endpointId))
                }
            }

        private val payloadCallback =
            object : PayloadCallback() {
                override fun onPayloadReceived(
                    endpointId: String,
                    payload: Payload,
                ) {
                    val bytes = payload.asBytes() ?: return
                    scope.launch {
                        val decoded = NearbyProtocol.decode(bytes)
                        if (decoded == null) {
                            Log.w(TAG, "Dropping undecodable payload from $endpointId")
                            return@launch
                        }
                        when (decoded) {
                            is RaidRequest -> handleRaidRequest(endpointId, decoded)
                            is ShadowNotice -> handleShadowNotice(decoded)
                            else -> _inbound.emit(InboundPayload(endpointId, decoded))
                        }
                    }
                }

                override fun onPayloadTransferUpdate(
                    endpointId: String,
                    update: PayloadTransferUpdate,
                ) {
                    // Byte payloads only; no transfer tracking needed.
                }
            }

        /**
         * Starts advertising + discovery for pairing or visits. In PAIRING mode
         * incoming connections wait for the "Match!" tap; in VISIT mode they
         * auto-accept.
         */
        fun startHosting(
            baseName: String,
            hello: Hello,
            mode: HostMode,
        ) {
            if (!p2pAvailable) return
            stopHosting()
            myBaseName = baseName
            myHello = hello
            hostMode = mode
            val strategy = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
            client
                .startAdvertising(myBaseName, NearbyProtocol.SERVICE_ID, connectionLifecycleCallback, strategy)
                .addOnFailureListener { e ->
                    Log.w(TAG, "startAdvertising failed", e)
                    _connectionEvents.tryEmit(ConnectionEvent.ConnectFailed("host"))
                }
            val discovery = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
            client
                .startDiscovery(NearbyProtocol.SERVICE_ID, endpointDiscoveryCallback, discovery)
                .addOnFailureListener { e ->
                    Log.w(TAG, "startDiscovery failed", e)
                    _connectionEvents.tryEmit(ConnectionEvent.ConnectFailed("host"))
                }
        }

        fun stopHosting() {
            if (!p2pAvailable) return
            runCatching { client.stopAdvertising() }
            runCatching { client.stopDiscovery() }
            runCatching { client.stopAllEndpoints() }
            pendingTokens.clear()
            connectedNames.clear()
            hostMode = null
        }

        /** Pairing: this device taps a discovered peer's base name. */
        fun requestPairing(peer: DiscoveredPeer) {
            if (!p2pAvailable) return
            pendingTokens[peer.endpointId] = TokenInfo(peer.baseName, token = "", incoming = false)
            client
                .requestConnection(myBaseName, peer.endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "requestConnection failed", e)
                    pendingTokens.remove(peer.endpointId)
                    _connectionEvents.tryEmit(ConnectionEvent.ConnectFailed(peer.endpointId))
                }
            _connectionEvents.tryEmit(
                ConnectionEvent.TokenReady(peer.endpointId, peer.baseName, authToken = "", incoming = false),
            )
        }

        /**
         * Visit mode: connect to a discovered endpoint (the RaidVisit screen
         * calls this for every discovered peer; strangers are disconnected after
         * the Hello guestId check).
         */
        fun connectTo(endpointId: String) {
            if (!p2pAvailable) return
            client
                .requestConnection(myBaseName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "connectTo failed", e)
                    _connectionEvents.tryEmit(ConnectionEvent.ConnectFailed(endpointId))
                }
        }

        /**
         * Accepts an incoming connection. In PAIRING mode the Friends screen
         * calls this after both kids tap "Match!". In VISIT mode the manager
         * already accepted automatically.
         */
        fun acceptConnection(endpointId: String) {
            if (!p2pAvailable) return
            client
                .acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "acceptConnection failed", e)
                    _connectionEvents.tryEmit(ConnectionEvent.ConnectFailed(endpointId))
                }
        }

        fun sendPayload(
            endpointId: String,
            payload: P2pPayload,
        ) {
            if (!p2pAvailable) return
            val bytes = NearbyProtocol.encode(payload)
            client
                .sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener { e -> Log.w(TAG, "sendPayload failed", e) }
        }

        fun disconnect(endpointId: String) {
            if (!p2pAvailable) return
            runCatching { client.disconnectFromEndpoint(endpointId) }
            connectedNames.remove(endpointId)
        }

        private suspend fun handleRaidRequest(
            endpointId: String,
            request: RaidRequest,
        ) {
            val responder =
                runCatching {
                    EntryPointAccessors
                        .fromApplication(context, RaidResponderEntryPoint::class.java)
                        .liveRaidResponder()
                }.getOrNull()
            if (responder == null) {
                Log.w(TAG, "No raid responder; rejecting raid ${request.raidId}")
                sendPayload(endpointId, RaidResult(raidId = request.raidId, accepted = false, reason = "empty"))
                return
            }
            val result =
                runCatching { responder.onRaidRequest(request) }.getOrElse { e ->
                    Log.w(TAG, "Raid responder failed", e)
                    RaidResult(raidId = request.raidId, accepted = false, reason = "empty")
                }
            sendPayload(endpointId, result)
        }

        private suspend fun handleShadowNotice(notice: ShadowNotice) {
            runCatching {
                EntryPointAccessors
                    .fromApplication(context, RaidResponderEntryPoint::class.java)
                    .liveRaidResponder()
                    .onShadowNotice(notice)
            }.onFailure { e -> Log.w(TAG, "Shadow notice handling failed", e) }
        }

        companion object {
            private const val TAG = "NearbyManager"
        }
    }

/**
 * Lazy entry point so the raid responder (which needs GameRepository) is
 * only instantiated when a raid payload actually arrives.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RaidResponderEntryPoint {
    fun liveRaidResponder(): LiveRaidResponder
}
