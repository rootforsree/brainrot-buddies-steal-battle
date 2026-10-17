package com.sreecharan.brainrot_buddies_steal_battle.ui.poster

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.InsetGroupedList
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/**
 * §7 PosterImport route (from Settings, or base decoration later).
 * Builder B: hang the saved poster in the base via
 * `repository.observePosterPath()` + the app-private file; it is never
 * uploaded, synced, shared, or shown to other players.
 */
@Composable
fun PosterRoute(
    onBack: () -> Unit,
    viewModel: PosterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) viewModel.onPhotoPicked(uri)
        }

    PosterScreenContent(
        state = state,
        actions =
            PosterActions(
                onBack = onBack,
                onAddPhoto = { picker.launch("image/*") },
                onConfirmPhoto = viewModel::onConfirmPhoto,
                onPickAnother = {
                    viewModel.onPickAnother()
                    picker.launch("image/*")
                },
                onRemovePoster = viewModel::onRemovePoster,
                onFailureDismissed = viewModel::onFailureDismissed,
            ),
        decodeFile = viewModel::decodePosterFile,
        savedAbsolutePath = viewModel.posterFile(state.posterRelativePath).absolutePath,
    )
}

/** All user actions on the poster import screen. */
data class PosterActions(
    val onBack: () -> Unit,
    val onAddPhoto: () -> Unit,
    val onConfirmPhoto: () -> Unit,
    val onPickAnother: () -> Unit,
    val onRemovePoster: () -> Unit,
    val onFailureDismissed: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterScreenContent(
    state: PosterUiState,
    actions: PosterActions,
    decodeFile: suspend (String) -> Bitmap?,
    savedAbsolutePath: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { PosterTopBar(scrollBehavior, actions.onBack) },
        containerColor = colors.groupedBackground,
    ) { padding ->
        InsetGroupedList(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + AppDimens.tabBarClearance,
                ),
        ) {
            posterFrameItem(state, actions, decodeFile, savedAbsolutePath)
            footerItems(state.failed, actions.onFailureDismissed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosterTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    LargeTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.poster_title),
                style = AppTypography.largeTitle,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_close),
                    tint = colors.accent,
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.groupedBackground,
                scrolledContainerColor = colors.groupedBackground,
            ),
    )
}

private fun LazyListScope.posterFrameItem(
    state: PosterUiState,
    actions: PosterActions,
    decodeFile: suspend (String) -> Bitmap?,
    savedAbsolutePath: String,
) {
    item(key = "frame", contentType = "frame") {
        val preview = state.previewPath
        when {
            preview != null -> {
                PosterPreview(
                    path = preview,
                    decodeFile = decodeFile,
                    isSaving = state.isSaving,
                    onConfirm = actions.onConfirmPhoto,
                    onRepick = actions.onPickAnother,
                )
            }

            state.hasPoster -> {
                SavedPoster(
                    path = savedAbsolutePath,
                    decodeFile = decodeFile,
                    onRepick = actions.onAddPhoto,
                    onRemove = actions.onRemovePoster,
                )
            }

            else -> {
                EmptyPosterFrame(onAddPhoto = actions.onAddPhoto)
            }
        }
    }
}

private fun LazyListScope.footerItems(
    failed: Boolean,
    onFailureDismissed: () -> Unit,
) {
    if (failed) {
        item(key = "failed", contentType = "message") {
            val colors = LocalAppColors.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = colors.warning.copy(alpha = 0.15f),
                    ),
            ) {
                TextButton(onClick = onFailureDismissed) {
                    Text(
                        text = stringResource(R.string.shop_error),
                        style = AppTypography.body,
                        color = colors.label,
                        modifier = Modifier.padding(AppDimens.spacing8),
                    )
                }
            }
        }
    }
    item(key = "terms", contentType = "terms") {
        val colors = LocalAppColors.current
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing4),
        ) {
            Text(
                text = stringResource(R.string.poster_terms),
                style = AppTypography.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.poster_private),
                style = AppTypography.caption,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The chunky frame the poster hangs in. */
@Composable
private fun ChunkyFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardSurface)
                .border(12.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun PosterFileImage(
    path: String,
    decodeFile: suspend (String) -> Bitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = decodeFile(path)
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(LocalAppColors.current.groupedBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun EmptyPosterFrame(onAddPhoto: () -> Unit) {
    val colors = LocalAppColors.current
    ChunkyFrame {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
            modifier = Modifier.padding(AppDimens.spacing16),
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = colors.secondaryLabel,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.poster_empty),
                style = AppTypography.headline,
                color = colors.label,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.poster_empty_sub),
                style = AppTypography.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAddPhoto,
                modifier = Modifier.heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) {
                Text(
                    text = stringResource(R.string.poster_add),
                    style = AppTypography.headline,
                )
            }
        }
    }
}

@Composable
private fun PosterPreview(
    path: String,
    decodeFile: suspend (String) -> Bitmap?,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onRepick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12)) {
        ChunkyFrame {
            PosterFileImage(
                path = path,
                decodeFile = decodeFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Button(
            onClick = onConfirm,
            enabled = !isSaving,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                )
            } else {
                Text(
                    text = stringResource(R.string.poster_use),
                    style = AppTypography.headline,
                )
            }
        }
        TextButton(onClick = onRepick, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.poster_repick),
                style = AppTypography.body,
                color = colors.secondaryLabel,
            )
        }
    }
}

@Composable
private fun SavedPoster(
    path: String,
    decodeFile: suspend (String) -> Bitmap?,
    onRepick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12)) {
        ChunkyFrame {
            PosterFileImage(
                path = path,
                decodeFile = decodeFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Button(
            onClick = onRepick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
            Text(
                text = stringResource(R.string.poster_repick),
                style = AppTypography.headline,
            )
        }
        TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.poster_remove),
                style = AppTypography.body,
                color = colors.destructive,
            )
        }
    }
}
