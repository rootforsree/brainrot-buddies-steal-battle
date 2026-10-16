package com.sreecharan.brainrot_buddies_steal_battle.p2p

/**
 * Export/import of base snapshots, per docs/contract.md §8.
 *
 * A snapshot is the saved shape of a friend's base: who sits where, the
 * base-wide shield, and the income rate. Snapshots stay under 2 KB on the
 * wire and refresh only on a new P2P exchange.
 */
object SnapshotCodec {

    /**
     * Builds a snapshot from live base data. [slots] holds one entry per
     * occupied slot; empty slots are simply absent.
     */
    fun export(
        guestId: String,
        baseName: String,
        shieldUntil: Long,
        coinsPerSec: Int,
        slots: List<SnapshotSlot>,
        exportedAt: Long,
    ): BaseSnapshot = BaseSnapshot(
        guestId = guestId,
        baseName = baseName,
        exportedAt = exportedAt,
        shieldUntil = shieldUntil,
        coinsPerSec = coinsPerSec,
        slots = slots.sortedBy { it.slot },
    )

    /** Serializes a snapshot for storage in FriendEntity.snapshotJson. */
    fun encode(snapshot: BaseSnapshot): String =
        NearbyProtocol.json.encodeToString(BaseSnapshot.serializer(), snapshot)

    /** Parses stored JSON back into a snapshot. Null when malformed or stale-version. */
    fun decode(json: String): BaseSnapshot? {
        val snapshot = runCatching {
            NearbyProtocol.json.decodeFromString(BaseSnapshot.serializer(), json)
        }.getOrNull() ?: return null
        if (snapshot.v != NearbyProtocol.PROTOCOL_VERSION) return null
        return snapshot
    }

    /** Encoded size check: snapshots must stay under 2 KB. */
    fun encodedSizeBytes(snapshot: BaseSnapshot): Int =
        encode(snapshot).toByteArray(Charsets.UTF_8).size

    fun isWithinSizeLimit(snapshot: BaseSnapshot): Boolean =
        encodedSizeBytes(snapshot) <= NearbyProtocol.MAX_SNAPSHOT_BYTES

    /** The buddy on [slot], or null when the slot is empty in the snapshot. */
    fun slotOf(snapshot: BaseSnapshot, slot: Int): SnapshotSlot? =
        snapshot.slots.firstOrNull { it.slot == slot }
}
