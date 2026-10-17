package com.sreecharan.brainrot_buddies_steal_battle.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.InsetGroupedList
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SectionHeader
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/**
 * Plain-language screen for parents: data practices, ad policy, purchases,
 * friends/chat, data deletion, contact. No ViewModel: static content.
 */
@Composable
fun ForParentsRoute(onBack: () -> Unit) {
    ForParentsScreenContent(onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForParentsScreenContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { ForParentsTopBar(scrollBehavior, onBack) },
        containerColor = colors.groupedBackground,
    ) { padding ->
        InsetGroupedList(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + AppDimens.tabBarClearance,
            ),
        ) {
            infoSection(
                key = "data",
                headerRes = R.string.forparents_data_title,
                bodyRes = R.string.forparents_data_body,
            )
            infoSection(
                key = "ads",
                headerRes = R.string.forparents_ads_title,
                bodyRes = R.string.forparents_ads_body,
            )
            infoSection(
                key = "purchases",
                headerRes = R.string.forparents_purchases_title,
                bodyRes = R.string.forparents_purchases_body,
            )
            infoSection(
                key = "friends",
                headerRes = R.string.forparents_friends_title,
                bodyRes = R.string.forparents_friends_body,
            )
            infoSection(
                key = "delete",
                headerRes = R.string.forparents_data_delete_title,
                bodyRes = R.string.forparents_data_delete_body,
            )
            item(key = "contact-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.forparents_contact_title))
            }
            item(key = "contact", contentType = "card") {
                ForParentsContactSection()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForParentsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    LargeTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.forparents_title),
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.groupedBackground,
            scrolledContainerColor = colors.groupedBackground,
        ),
    )
}

private fun LazyListScope.infoSection(
    key: String,
    headerRes: Int,
    bodyRes: Int,
) {
    item(key = "$key-header", contentType = "header") {
        SectionHeader(title = stringResource(headerRes))
    }
    item(key = key, contentType = "card") {
        ParentInfoCard(bodyRes = bodyRes)
    }
}

@Composable
private fun ForParentsContactSection() {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    GroupCard {
        Column(
            modifier = Modifier.padding(AppDimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Text(
                text = stringResource(R.string.forparents_contact_body),
                style = AppTypography.body,
                color = colors.label,
            )
            Button(
                onClick = {
                    val url = context.getString(R.string.about_github_url)
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = AppShapes.capsule,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                ),
            ) {
                Text(text = stringResource(R.string.forparents_contact_button))
            }
        }
    }
}

@Composable
private fun ParentInfoCard(bodyRes: Int) {
    val colors = LocalAppColors.current
    GroupCard {
        Text(
            text = stringResource(bodyRes),
            style = AppTypography.body,
            color = colors.label,
            modifier = Modifier.padding(AppDimens.spacing16),
        )
    }
}
