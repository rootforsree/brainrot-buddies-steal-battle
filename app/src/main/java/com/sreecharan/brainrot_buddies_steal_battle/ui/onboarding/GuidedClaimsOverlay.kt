package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sreecharan.brainrot_buddies_steal_battle.R

/**
 * Coach mark for one guided-claim step. Builder B places this on the Base
 * screen near the relevant target. Non-blocking: taps pass through everywhere
 * except the card itself, and "Later" hides the step for the session.
 */
@Composable
fun GuidedClaimsCoachMark(
    step: GuidedStep,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tip = when (step) {
        GuidedStep.CLAIM_FROGBOOT -> stringResource(R.string.guided_tip_frogboot)
        GuidedStep.CLAIM_PIP -> stringResource(R.string.guided_tip_pip)
        GuidedStep.BUY_SLOT -> stringResource(R.string.guided_tip_slot)
        GuidedStep.CLAIM_SPLORCH -> stringResource(R.string.guided_tip_splorch)
        GuidedStep.COLLECT_VAULT -> stringResource(R.string.guided_tip_vault)
        GuidedStep.OPEN_GIFT -> stringResource(R.string.guided_tip_gift)
    }
    BennyTip(
        tip = tip,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
