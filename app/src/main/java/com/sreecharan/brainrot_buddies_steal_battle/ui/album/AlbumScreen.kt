package com.sreecharan.brainrot_buddies_steal_battle.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Sticker book: 34 buddies + 10 "coming soon" silhouettes. One screen, one
 * job: browse. Tap a caught buddy to hear its catchphrase. Tier completion
 * bonuses are claimed with a single tap (free money: mis-taps cost nothing).
 */
@Composable
fun AlbumRoute(viewModel: AlbumViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    AlbumEventEffect(viewModel = viewModel, message = message) { message = it }

    AlbumScreenContent(
        state = state,
        message = message,
        onStickerTapped = viewModel::onStickerTapped,
        onClaimBonus = viewModel::onClaimBonus,
    )
}

/** Sticker grid columns. */
private const val GRID_COLUMNS = 3

/** Sticker card aspect ratio. */
private const val CARD_ASPECT_RATIO = 0.8f

/** How long a toast message stays visible. */
private const val MESSAGE_DISMISS_MS = 2_500L

@Composable
private fun AlbumEventEffect(
    viewModel: AlbumViewModel,
    message: String?,
    onMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            onMessage(
                when (event) {
                    is AlbumEvent.BonusClaimed -> {
                        context.getString(R.string.album_claim, event.amount)
                    }

                    AlbumEvent.ClaimFailed -> {
                        context.getString(R.string.shop_error)
                    }
                },
            )
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DISMISS_MS)
            onMessage(null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreenContent(
    state: AlbumUiState,
    message: String?,
    onStickerTapped: (Sticker) -> Unit,
    onClaimBonus: (Rarity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AlbumTopBar(scrollBehavior) },
        containerColor = colors.groupedBackground,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + AppDimens.tabBarClearance,
                    ),
            contentPadding = PaddingValues(horizontal = AppDimens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            messageItem(message)
            state.bonuses.forEach { bonus ->
                tierSection(
                    bonus = bonus,
                    stickers =
                        when (bonus.rarity) {
                            Rarity.COMMON -> state.commons
                            Rarity.RARE -> state.rares
                            Rarity.EPIC -> state.epics
                        },
                    onClaimBonus = onClaimBonus,
                    onStickerTapped = onStickerTapped,
                )
            }
            comingSoonSection(state.comingSoon)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    val colors = LocalAppColors.current
    LargeTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.album_title),
                style = AppTypography.largeTitle,
            )
        },
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.groupedBackground,
                scrolledContainerColor = colors.groupedBackground,
            ),
    )
}

private fun LazyGridScope.messageItem(message: String?) {
    if (message != null) {
        item(span = { GridItemSpan(GRID_COLUMNS) }, key = "message", contentType = "message") {
            val colors = LocalAppColors.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = colors.accent.copy(alpha = 0.15f),
                    ),
            ) {
                Text(
                    text = message,
                    style = AppTypography.body,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.spacing12),
                )
            }
        }
    }
}

private fun LazyGridScope.tierSection(
    bonus: TierBonusState,
    stickers: List<Sticker>,
    onClaimBonus: (Rarity) -> Unit,
    onStickerTapped: (Sticker) -> Unit,
) {
    item(
        span = { GridItemSpan(GRID_COLUMNS) },
        key = "tier-${bonus.rarity}",
        contentType = "tier",
    ) {
        TierHeader(
            bonus = bonus,
            onClaim = { onClaimBonus(bonus.rarity) },
        )
    }
    items(
        items = stickers,
        key = { it.buddyId },
        contentType = { "sticker" },
    ) { sticker ->
        StickerCard(
            sticker = sticker,
            onTap = { onStickerTapped(sticker) },
        )
    }
}

private fun LazyGridScope.comingSoonSection(comingSoon: List<ComingSoonBuddy>) {
    item(span = { GridItemSpan(GRID_COLUMNS) }, key = "soon-header", contentType = "tier") {
        Text(
            text = stringResource(R.string.album_section_soon),
            style = AppTypography.title2,
            color = LocalAppColors.current.label,
            modifier = Modifier.padding(top = AppDimens.spacing8),
        )
    }
    itemsIndexed(
        items = comingSoon,
        key = { index, _ -> "soon-$index" },
        contentType = { _, _ -> "coming-soon" },
    ) { _, soon ->
        ComingSoonCard(
            kindLabel =
                stringResource(
                    if (soon.kind == "Legendary") {
                        R.string.album_kind_legendary
                    } else {
                        R.string.album_kind_mythic
                    },
                ),
        )
    }
}

@Composable
private fun TierHeader(
    bonus: TierBonusState,
    onClaim: () -> Unit,
) {
    val colors = LocalAppColors.current
    val title =
        when (bonus.rarity) {
            Rarity.COMMON -> stringResource(R.string.album_section_commons)
            Rarity.RARE -> stringResource(R.string.album_section_rares)
            Rarity.EPIC -> stringResource(R.string.album_section_epics)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.spacing8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = title, style = AppTypography.title2, color = colors.label)
            Text(
                text = stringResource(R.string.album_progress, bonus.caught, bonus.total),
                style = AppTypography.footnote,
                color = colors.secondaryLabel,
            )
        }
        when {
            bonus.claimable -> {
                Button(
                    onClick = onClaim,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) {
                    Text(text = stringResource(R.string.album_claim, bonus.amount))
                }
            }

            bonus.claimed -> {
                Text(
                    text = stringResource(R.string.album_claimed),
                    style = AppTypography.headline,
                    color = colors.success,
                )
            }
        }
    }
}

@Composable
private fun StickerCard(
    sticker: Sticker,
    onTap: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(AppShapes.card)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.album_tap_hint),
                    onClick = onTap,
                    enabled = sticker.caught,
                ),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BuddyImage(
                imageName = "buddy_${sticker.buddyId}",
                contentDescription = if (sticker.caught) sticker.name else null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                colorFilter =
                    if (sticker.caught) {
                        null
                    } else {
                        ColorFilter.tint(Color.Black)
                    },
            )
            Text(
                text = if (sticker.caught) sticker.name else stringResource(R.string.album_unknown),
                style = AppTypography.footnote,
                color = if (sticker.caught) colors.label else colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ComingSoonCard(kindLabel: String) {
    val colors = LocalAppColors.current
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT_RATIO),
        shape = AppShapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = colors.cardSurface.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BuddyImage(
                imageName = "buddy_placeholder",
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.85f)),
            )
            Text(
                text = stringResource(R.string.album_unknown),
                style = AppTypography.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "$kindLabel \u00b7 ${stringResource(R.string.album_coming_soon)}",
                style = AppTypography.caption,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}
