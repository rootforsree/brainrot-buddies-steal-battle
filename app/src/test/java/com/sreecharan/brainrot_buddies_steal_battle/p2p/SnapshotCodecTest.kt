package com.sreecharan.brainrot_buddies_steal_battle.p2p

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotCodecTest {

    private fun snapshot() = SnapshotCodec.export(
        guestId = "guest-2",
        baseName = "Noodle Nest",
        shieldUntil = 0L,
        coinsPerSec = 49,
        slots = listOf(
            SnapshotSlot(slot = 0, buddyId = "frogboot", level = 3),
            SnapshotSlot(slot = 5, buddyId = "gulp", level = 1),
        ),
        exportedAt = 1_700_000_000_000L,
    )

    @Test
    fun `export sorts slots and round-trips through json`() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot()))
        assertEquals(snapshot(), decoded)
    }

    @Test
    fun `slots come back sorted by slot index`() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot()))
        assertEquals(listOf(0, 5), decoded?.slots?.map { it.slot })
    }

    @Test
    fun `malformed json decodes to null`() {
        assertNull(SnapshotCodec.decode("definitely not json"))
        assertNull(SnapshotCodec.decode(""))
    }

    @Test
    fun `stale protocol version decodes to null`() {
        val tampered = SnapshotCodec.encode(snapshot()).replace("\"v\":1", "\"v\":2")
        assertNull(SnapshotCodec.decode(tampered))
    }

    @Test
    fun `a full 24-slot snapshot stays under the 2KB wire limit`() {
        val full = SnapshotCodec.export(
            guestId = "guest-2",
            baseName = "Noodle Nest",
            shieldUntil = 0L,
            coinsPerSec = 960,
            slots = (0 until 24).map { SnapshotSlot(slot = it, buddyId = "sassy_soda", level = 3) },
            exportedAt = 1_700_000_000_000L,
        )
        assertTrue(SnapshotCodec.isWithinSizeLimit(full))
    }

    @Test
    fun `slotOf finds the buddy or returns null`() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot()))!!
        assertEquals("frogboot", SnapshotCodec.slotOf(decoded, 0)?.buddyId)
        assertNull(SnapshotCodec.slotOf(decoded, 3))
    }
}
