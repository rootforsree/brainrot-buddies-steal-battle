package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.JuiceController

/**
 * Contract §7 route `Base`: the game board. Stateful route (skill pattern):
 * owns the [HomeViewModel], the frame clock for the Canvas boards, the
 * [JuiceController], and first-tap audio unlock. [HomeScreen] stays stateless.
 *
 * Cross-tab navigation is a callback seam: the nav host wires the real
 * destinations, and the default no-op keeps the route previewable.
 */
@Composable
fun BaseScreen(
    onNavigateToFriends: () -> Unit = {},
    onNavigateToShop: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // One frame clock for every Canvas on this screen, read late in draw scope.
    val frameTime = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTime.longValue = it }
        }
    }

    val juice = remember { JuiceController() }

    // Trauma impulses apply once per token (the shake never sustains, §10).
    LaunchedEffect(state.value.trauma.token) {
        val trauma = state.value.trauma
        if (trauma.amount > 0f) juice.shake.add(trauma.amount)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Haptic -> {
                    haptics.performHapticFeedback(
                        when (event.kind) {
                            HapticKind.TAP -> HapticFeedbackType.TextHandleMove
                            HapticKind.SUCCESS -> HapticFeedbackType.LongPress
                            HapticKind.CELEBRATION -> HapticFeedbackType.LongPress
                            HapticKind.WARNING -> HapticFeedbackType.Reject
                        },
                    )
                }

                HomeEvent.BigMoment -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                HomeEvent.NavigateToFriends -> {
                    onNavigateToFriends()
                }

                HomeEvent.NavigateToShop -> {
                    onNavigateToShop()
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Contract §9: no audio before the first tap. Every intent also
                // unlocks, but a tap that hits no control still counts.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        viewModel.onFirstInteraction()
                    }
                },
    ) {
        HomeScreen(
            state = state.value,
            frameTime = frameTime,
            juice = juice,
            onEvent = viewModel::onUiEvent,
        )
    }
}
