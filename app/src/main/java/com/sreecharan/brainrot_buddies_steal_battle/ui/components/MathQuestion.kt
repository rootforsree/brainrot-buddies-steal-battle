package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.random.Random

/** Pure math-question generator: unit-testable, no Android dependency. */
data class MathQuestion(
    val text: String,
    val options: ImmutableList<Int>,
    val answer: Int,
)

fun generateMathQuestion(random: Random = Random.Default): MathQuestion {
    val a = random.nextInt(OPERAND_MIN, OPERAND_MAX_EXCLUSIVE)
    val b = random.nextInt(OPERAND_MIN, OPERAND_MAX_EXCLUSIVE)
    val answer = a + b
    val options = LinkedHashSet<Int>()
    options += answer
    var guard = 0
    while (options.size < REQUIRED_OPTION_COUNT && guard < DISTRACTOR_GUARD_MAX) {
        guard++
        val delta = random.nextInt(DISTRACTOR_DELTA_MIN, DISTRACTOR_DELTA_MAX_EXCLUSIVE)
        if (delta == 0) continue
        val candidate = answer + delta
        if (candidate > 0) options += candidate
    }
    // Fallback: should never trigger, keeps the 4-option promise.
    var filler = answer + 1
    while (options.size < REQUIRED_OPTION_COUNT) {
        options += filler
        filler++
    }
    return MathQuestion(
        text = "$a + $b = ?",
        options = options.shuffled(random).toImmutableList(),
        answer = answer,
    )
}

private const val OPERAND_MIN = 3
private const val OPERAND_MAX_EXCLUSIVE = 13
private const val REQUIRED_OPTION_COUNT = 4
private const val DISTRACTOR_GUARD_MAX = 100
private const val DISTRACTOR_DELTA_MIN = -6
private const val DISTRACTOR_DELTA_MAX_EXCLUSIVE = 7
