package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlin.random.Random
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException

/**
 * Reusable parent gate: hold 3 seconds, then a grown-up math question.
 * Used on IAP, the ads toggle, friend codes and P2P pairing (contract §1).
 * Builder D: use [ParentGateDialog] around those flows.
 *
 * Coin purchases use [HoldToConfirmButton] directly (mis-tap protection,
 * no math). The math step is reserved for the parental gate.
 */

/** Pure math-question generator: unit-testable, no Android dependency. */
data class MathQuestion(
    val text: String,
    val options: ImmutableList<Int>,
    val answer: Int,
)

fun generateMathQuestion(random: Random = Random.Default): MathQuestion {
    val a = random.nextInt(3, 13)
    val b = random.nextInt(3, 13)
    val answer = a + b
    val options = LinkedHashSet<Int>()
    options += answer
    var guard = 0
    while (options.size < 4 && guard < 100) {
        guard++
        val delta = random.nextInt(-6, 7)
        if (delta == 0) continue
        val candidate = answer + delta
        if (candidate > 0) options += candidate
    }
    // Fallback: should never trigger, keeps the 4-option promise.
    var filler = answer + 1
    while (options.size < 4) {
        options += filler
        filler++
    }
    return MathQuestion(
        text = "$a + $b = ?",
        options = options.shuffled(random).toImmutableList(),
        answer = answer,
    )
}

/**
 * Press-and-hold button. The fill sweeps while held; releasing early resets
 * with no side effect, so mis-taps cost nothing. TalkBack's double-tap-and-hold
 * gesture drives the same press path.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 3000,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val progress = remember { Animatable(0f) }
    val holdDescription = stringResource(R.string.gate_hold)
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(AppShapes.capsule)
            .background(if (enabled) colors.accent else colors.secondaryLabel.copy(alpha = 0.3f))
            .pointerInput(enabled, holdMillis, onConfirm) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        try {
                            progress.animateTo(1f, tween(holdMillis))
                            onConfirm()
                        } catch (_: CancellationException) {
                            // Released early: no-op, progress resets below.
                        }
                        progress.snapTo(0f)
                    },
                )
            }
            .semantics { contentDescription = "$text. $holdDescription" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.value)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.25f)),
        )
        Text(
            text = text,
            style = AppTypography.headline,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppDimens.spacing16),
        )
    }
}

private enum class GateStep { HOLD, MATH }

@Composable
fun ParentGateDialog(
    onPassed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.gate_title),
    requireMath: Boolean = true,
) {
    var step by remember { mutableStateOf(GateStep.HOLD) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.spacing24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
            ) {
                BuddyImage(
                    imageName = "hero_sammy",
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = title,
                    style = AppTypography.title2,
                    color = LocalAppColors.current.label,
                    textAlign = TextAlign.Center,
                )
                when (step) {
                    GateStep.HOLD -> {
                        Text(
                            text = stringResource(R.string.gate_hold),
                            style = AppTypography.body,
                            color = LocalAppColors.current.secondaryLabel,
                            textAlign = TextAlign.Center,
                        )
                        HoldToConfirmButton(
                            text = stringResource(R.string.gate_hold),
                            onConfirm = {
                                if (requireMath) {
                                    step = GateStep.MATH
                                } else {
                                    onPassed()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GateStep.MATH -> {
                        MathQuestionStep(onPassed = onPassed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.gate_cancel),
                        style = AppTypography.body,
                        color = LocalAppColors.current.secondaryLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun MathQuestionStep(onPassed: () -> Unit) {
    val colors = LocalAppColors.current
    var question by remember { mutableStateOf(generateMathQuestion()) }
    var wrong by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
    ) {
        Text(
            text = stringResource(R.string.gate_math),
            style = AppTypography.body,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Text(
            text = question.text,
            style = AppTypography.largeTitle,
            color = colors.label,
        )
        if (wrong) {
            Text(
                text = stringResource(R.string.gate_wrong),
                style = AppTypography.headline,
                color = colors.warning,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
            question.options.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { option ->
                        Button(
                            onClick = {
                                if (option == question.answer) {
                                    onPassed()
                                } else {
                                    wrong = true
                                    question = generateMathQuestion()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp),
                            shape = AppShapes.card,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent.copy(alpha = 0.15f),
                                contentColor = colors.label,
                            ),
                        ) {
                            Text(text = option.toString(), style = AppTypography.title2)
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
