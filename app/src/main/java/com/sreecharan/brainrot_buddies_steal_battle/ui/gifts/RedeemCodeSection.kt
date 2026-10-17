package com.sreecharan.brainrot_buddies_steal_battle.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Redeem-code entry, embedded in Settings. Codes are validated by the game
 * engine (Builder A); one use per guest ID. Known v1 codes: STEALFUN26
 * (500 coins + party emote), BUDDYUP26 (1 Lucky Ticket).
 */
@Composable
fun RedeemCodeSection(
    redeemState: RedeemUiState,
    onRedeem: (String) -> Unit,
    onResultConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    RedeemResultBanner(
        result = redeemState.result,
        onResultConsumed = onResultConsumed,
    )
    RedeemInputRow(
        code = code,
        onCodeChange = { code = it },
        isWorking = redeemState.isWorking,
        onRedeem = onRedeem,
        modifier = modifier,
    )
}

/** How long a redeem result banner stays visible. */
private const val RESULT_DISMISS_MS = 3_000L

/** Maximum redeem-code length (all v1 codes are 9 chars). */
private const val MAX_CODE_LENGTH = 16

@Composable
private fun RedeemResultBanner(
    result: RedeemResultUi?,
    onResultConsumed: () -> Unit,
) {
    if (result == null) return
    val colors = LocalAppColors.current
    val message = when (result) {
        is RedeemResultUi.Success -> {
            val parts = buildList {
                if (result.coins > 0) add(stringResource(R.string.coins_count, result.coins))
                if (result.luckyTickets > 0) {
                    add(stringResource(R.string.base_lucky_ticket, result.luckyTickets))
                }
                if (result.megaTickets > 0) {
                    add(stringResource(R.string.base_mega_ticket, result.megaTickets))
                }
            }
            // The STEALFUN26 party emote has no §5 home yet; coins are
            // granted and the emote gap is flagged for the coordinator.
            stringResource(R.string.shop_bought) + " " + parts.joinToString(", ")
        }
        RedeemResultUi.Invalid -> stringResource(R.string.gifts_redeem_invalid)
        RedeemResultUi.AlreadyUsed -> stringResource(R.string.gifts_redeem_used)
        RedeemResultUi.Error -> stringResource(R.string.shop_error)
    }
    LaunchedEffect(result) {
        delay(RESULT_DISMISS_MS)
        onResultConsumed()
    }
    Text(
        text = message,
        style = AppTypography.body,
        color = when (result) {
            is RedeemResultUi.Success -> colors.success
            else -> colors.warning
        },
        modifier = Modifier.padding(bottom = AppDimens.spacing8),
    )
}

@Composable
private fun RedeemInputRow(
    code: String,
    onCodeChange: (String) -> Unit,
    isWorking: Boolean,
    onRedeem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    GroupCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(AppDimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Text(
                text = stringResource(R.string.gifts_redeem_title),
                style = AppTypography.headline,
                color = colors.label,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        onCodeChange(
                            input.uppercase()
                                .filter { it.isLetterOrDigit() }
                                .take(MAX_CODE_LENGTH),
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.gifts_redeem_hint),
                            style = AppTypography.body,
                        )
                    },
                    singleLine = true,
                    enabled = !isWorking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.card,
                )
                Button(
                    onClick = { onRedeem(code) },
                    enabled = code.isNotBlank() && !isWorking,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = AppShapes.capsule,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) {
                    Text(text = stringResource(R.string.gifts_redeem_button))
                }
            }
        }
    }
}
