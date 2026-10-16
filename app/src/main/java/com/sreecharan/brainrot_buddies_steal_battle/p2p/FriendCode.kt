package com.sreecharan.brainrot_buddies_steal_battle.p2p

import java.security.MessageDigest
import java.util.Random

/**
 * 6-letter friend codes, per docs/contract.md §8.
 *
 * Charset ABCDEFGHJKMNPQRSTUVWXYZ23456789 (no 0/O/1/I/L, so codes read
 * clearly when kids read them out loud). A player's own code is derived
 * deterministically from their guest ID, so it survives reinstalls of the
 * same guest ID and needs no storage.
 */
object FriendCode {

    const val LENGTH = 6
    const val CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /**
     * Random code, for one-off uses. Pass a seeded [Random] in tests.
     */
    fun generate(random: Random = java.security.SecureRandom()): String {
        val chars = CharArray(LENGTH) { CHARSET[random.nextInt(CHARSET.length)] }
        return chars.concatToString()
    }

    /**
     * This device's stable code, derived from the guest ID (SHA-256, first
     * 6 bytes mapped into the charset). Shown behind the parent gate.
     */
    fun deriveFrom(guestId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(guestId.toByteArray(Charsets.UTF_8))
        val chars = CharArray(LENGTH) { i ->
            CHARSET[(digest[i].toInt() and BYTE_MASK) % CHARSET.length]
        }
        return chars.concatToString()
    }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in CHARSET }

    /** Masks a signed byte to its unsigned value when indexing the charset. */
    private const val BYTE_MASK = 0xFF
}
