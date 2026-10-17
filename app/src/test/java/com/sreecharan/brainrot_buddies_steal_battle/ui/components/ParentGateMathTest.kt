package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParentGateMathTest {
    @Test
    fun `question has four unique positive options containing the answer`() {
        repeat(200) { seed ->
            val question = generateMathQuestion(Random(seed))
            assertEquals(4, question.options.size)
            assertEquals(4, question.options.toSet().size)
            assertTrue(question.options.all { it > 0 })
            assertTrue(question.answer in question.options)
        }
    }

    @Test
    fun `question text matches the answer`() {
        repeat(50) { seed ->
            val question = generateMathQuestion(Random(seed))
            // Text is "a + b = ?": parse and verify.
            val parts = question.text.removeSuffix("= ?").split("+")
            val a = parts[0].trim().toInt()
            val b = parts[1].trim().toInt()
            assertEquals(a + b, question.answer)
            assertTrue(a in 3..12)
            assertTrue(b in 3..12)
        }
    }

    @Test
    fun `questions vary across seeds`() {
        val texts = (0 until 20).map { generateMathQuestion(Random(it)).text }.toSet()
        assertTrue(texts.size > 1)
    }
}
