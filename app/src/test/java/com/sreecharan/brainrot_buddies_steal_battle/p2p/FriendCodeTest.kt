package com.sreecharan.brainrot_buddies_steal_battle.p2p

import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FriendCodeTest {
    @Test
    fun `generated code is 6 chars from the frozen charset`() {
        val code = FriendCode.generate(Random(42))
        assertEquals(6, code.length)
        assertTrue(code.all { it in FriendCode.CHARSET })
    }

    @Test
    fun `generated code is deterministic for a seeded random`() {
        assertEquals(FriendCode.generate(Random(7)), FriendCode.generate(Random(7)))
    }

    @Test
    fun `different seeds give different codes`() {
        assertNotEquals(FriendCode.generate(Random(1)), FriendCode.generate(Random(2)))
    }

    @Test
    fun `charset excludes ambiguous characters`() {
        listOf('0', 'O', '1', 'I', 'L').forEach { ambiguous ->
            assertFalse(ambiguous in FriendCode.CHARSET, "$ambiguous must not be in the charset")
        }
        assertEquals(30, FriendCode.CHARSET.length)
    }

    @Test
    fun `isValid accepts only 6 charset chars`() {
        assertTrue(FriendCode.isValid("ABCDEF"))
        assertTrue(FriendCode.isValid("234567"))
        assertFalse(FriendCode.isValid("ABCDE"))
        assertFalse(FriendCode.isValid("ABCDEFG"))
        assertFalse(FriendCode.isValid("ABCDE1"))
        assertFalse(FriendCode.isValid("ABCDEO"))
        assertFalse(FriendCode.isValid("abcdef"))
        assertFalse(FriendCode.isValid(""))
    }

    @Test
    fun `deriveFrom is deterministic and valid`() {
        val first = FriendCode.deriveFrom("guest-123")
        val second = FriendCode.deriveFrom("guest-123")
        assertEquals(first, second)
        assertTrue(FriendCode.isValid(first))
    }

    @Test
    fun `deriveFrom differs per guest id`() {
        assertNotEquals(FriendCode.deriveFrom("guest-1"), FriendCode.deriveFrom("guest-2"))
    }
}
