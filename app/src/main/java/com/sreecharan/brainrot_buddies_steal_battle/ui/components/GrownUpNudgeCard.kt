package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/**
 * V1.7 "Show a grown-up!" nudge (contract §2).
 *
 * Shown after big moments: Epic pull, steal, revenge. It is a plain card for
 * the kid to show a parent in person. It NEVER prompts the kid to post, and
 * it is NEVER a share intent (contract §3: share-to-social is cut).
 *
 * Builder B: show after an Epic pull (claim flow).
 * Builder D: show after a steal and after a revenge.
 */
enum class NudgeKind { EPIC_PULL, STEAL, REVENGE }

@Composable
fun GrownUpNudgeDialog(
    kind: NudgeKind,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    buddyName: String? = null,
) {
    val colors = LocalAppColors.current
    val headline = when (kind) {
        NudgeKind.EPIC_PULL -> stringResource(R.string.nudge_epic)
        NudgeKind.STEAL -> stringResource(R.string.nudge_steal)
        NudgeKind.REVENGE -> stringResource(R.string.nudge_revenge)
    }
    Dialog(onDismissRequest = onDone) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.spacing24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
            ) {
                BuddyImage(
                    imageName = "hero_flip",
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                )
                Text(
                    text = stringResource(R.string.nudge_title),
                    style = AppTypography.title2,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = headline,
                    style = AppTypography.headline,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                if (buddyName != null) {
                    Text(
                        text = buddyName,
                        style = AppTypography.body,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = stringResource(R.string.nudge_sub),
                    style = AppTypography.body,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) {
                    Text(
                        text = stringResource(R.string.nudge_ok),
                        style = AppTypography.headline,
                    )
                }
            }
        }
    }
}
