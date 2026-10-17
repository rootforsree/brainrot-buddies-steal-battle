package com.sreecharan.brainrot_buddies_steal_battle.p2p

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbyProtocolTest {

    @Test
    fun `hello round-trips with v1`() {
        val hello = Hello(guestId = "guest-1", baseName = "Wobble Tower", code = "ABCDEF")
        val decoded = NearbyProtocol.decode(NearbyProtocol.encode(hello))
        assertEquals(hello, decoded)
    }

    @Test
    fun `snapshot round-trips with v1`() {
        val snapshot = BaseSnapshot(
            guestId = "guest-1",
            baseName = "Snack Shack",
            exportedAt = 1_700_000_000_000L,
            shieldUntil = 0L,
            coinsPerSec = 49,
            slots = listOf(
                SnapshotSlot(slot = 0, buddyId = "frogboot", level = 2),
                SnapshotSlot(slot = 3, buddyId = "sassy_soda", level = 1),
            ),
        )
        val decoded = NearbyProtocol.decode(NearbyProtocol.encode(snapshot))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `raid request round-trips with v1`() {
        val request = RaidRequest(
            raidId = "raid-1",
            attackerGuestId = "guest-1",
            attackerBaseName = "Wobble Tower",
            slot = 3,
        )
        assertEquals(request, NearbyProtocol.decode(NearbyProtocol.encode(request)))
    }

    @Test
    fun `raid result round-trips with v1`() {
        val accepted = RaidResult(raidId = "raid-1", accepted = true, buddyId = "gulp", buddyLevel = 2)
        assertEquals(accepted, NearbyProtocol.decode(NearbyProtocol.encode(accepted)))

        val rejected = RaidResult(raidId = "raid-2", accepted = false, reason = "shielded")
        assertEquals(rejected, NearbyProtocol.decode(NearbyProtocol.encode(rejected)))
    }

    @Test
    fun `chat round-trips with v1`() {
        val chat = P2PChat(kind = "phrase", refId = "3")
        assertEquals(chat, NearbyProtocol.decode(NearbyProtocol.encode(chat)))
    }

    @Test
    fun `shadow notice round-trips with v1`() {
        val notice = ShadowNotice(
            raidId = "raid-9",
            buddyId = "gulp",
            buddyLevel = 1,
            attackerGuestId = "guest-2",
            attackerBaseName = "Noodle Nest",
            createdAt = 1_700_000_000_000L,
        )
        assertEquals(notice, NearbyProtocol.decode(NearbyProtocol.encode(notice)))
    }

    @Test
    fun `every payload carries v equals 1 on the wire`() {
        val payloads: List<P2pPayload> = listOf(
            Hello(guestId = "g", baseName = "b", code = "ABCDEF"),
            BaseSnapshot("g", "b", 0L, 0L, 0, emptyList()),
            RaidRequest("r", "g", "b", 0),
            RaidResult("r", true),
            P2PChat("phrase", "0"),
            ShadowNotice("r", "frogboot", 1, "g", "b", 0L),
        )
        payloads.forEach { payload ->
            val wire = NearbyProtocol.encode(payload).toString(Charsets.UTF_8)
            assertTrue(wire.contains("\"v\":1"), "payload $payload must carry \"v\":1")
        }
    }

    @Test
    fun `version mismatch is rejected`() {
        val hello = Hello(v = 2, guestId = "g", baseName = "b", code = "ABCDEF")
        assertNull(NearbyProtocol.decode(NearbyProtocol.encode(hello)))
    }

    @Test
    fun `unknown envelope type is rejected`() {
        val envelope = PayloadEnvelope(type = "nonsense", body = "{}")
        val bytes = NearbyProtocol.json.encodeToString(PayloadEnvelope.serializer(), envelope)
            .toByteArray(Charsets.UTF_8)
        assertNull(NearbyProtocol.decode(bytes))
    }

    @Test
    fun `garbage bytes are rejected without throwing`() {
        assertNull(NearbyProtocol.decode("not json at all".toByteArray(Charsets.UTF_8)))
        assertNull(NearbyProtocol.decode(ByteArray(0)))
    }

    @Test
    fun `oversized payloads are rejected`() {
        assertNull(NearbyProtocol.decode(ByteArray(NearbyProtocol.MAX_PAYLOAD_BYTES + 1)))
    }

    @Test
    fun `oversized snapshot bodies are rejected`() {
        val huge = SnapshotCodec.export(
            SnapshotCodec.SnapshotExport(
                guestId = "guest-2",
                baseName = "x".repeat(3000),
                shieldUntil = 0L,
                coinsPerSec = 0,
                slots = emptyList(),
                exportedAt = 0L,
            ),
        )
        assertTrue(SnapshotCodec.encodedSizeBytes(huge) > NearbyProtocol.MAX_SNAPSHOT_BYTES)
        assertNull(NearbyProtocol.decode(NearbyProtocol.encode(huge)))
    }

    @Test
    fun `decoded payload is non-null and typed`() {
        val decoded = NearbyProtocol.decode(
            NearbyProtocol.encode(Hello(guestId = "g", baseName = "b", code = "ABCDEF")),
        )
        assertNotNull(decoded)
        assertTrue(decoded is Hello)
    }
}
