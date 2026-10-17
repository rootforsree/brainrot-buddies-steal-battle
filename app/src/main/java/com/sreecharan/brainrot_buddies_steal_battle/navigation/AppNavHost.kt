package com.sreecharan.brainrot_buddies_steal_battle.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.album.AlbumRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GlassTabBar
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.TabItem
import com.sreecharan.brainrot_buddies_steal_battle.ui.friends.FriendsRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.BaseScreen
import com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding.OnboardingFlow
import com.sreecharan.brainrot_buddies_steal_battle.ui.raids.RaidVisitRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.settings.ForParentsRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.poster.PosterRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.settings.SettingsRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.shop.ShopRoute
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One activity, one root NavHost, type-safe routes (contract §7).
 *
 * Screen ownership per tab:
 * - Base tab: Builder B's `ui.home.BaseScreen()`; `RaidVisit` pushes
 *   Builder D's `ui.raids.RaidVisitRoute()` (friendGuestId via SavedStateHandle).
 * - Shop tab: Builder C `ui.shop.ShopRoute()`.
 * - Album tab: Builder C `ui.album.AlbumRoute()` (appears on day 5+).
 * - Friends tab: Builder D's `ui.friends.FriendsRoute()`.
 * - More tab: Builder C settings graph (`SettingsRoute`, `ForParentsRoute`,
 *   `PosterRoute`).
 */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val gateViewModel: NavGateViewModel = hiltViewModel()
    val gate by gateViewModel.uiState.collectAsStateWithLifecycle()

    if (gate.loading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(LocalAppColors.current.groupedBackground),
        )
        return
    }
    if (!gate.hasBaseName) {
        OnboardingFlow(modifier = modifier)
        return
    }
    MainTabs(
        showAlbumTab = gate.onboardingDay >= 5,
        modifier = modifier,
    )
}

private data class MainTab(
    val graph: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
private fun MainTabs(showAlbumTab: Boolean, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val tabs = rememberMainTabs(showAlbumTab)
    // One activity, one root NavHost. The glass tab bar floats over content
    // and never hides during push navigation; per-tab state survives switches
    // through saveState/restoreState, and screens reserve bottom clearance.
    Box(modifier = modifier.fillMaxSize()) {
        MainNavGraph(navController)
        MainTabBar(
            tabs = tabs,
            destination = destination,
            onTabSelected = { index ->
                navController.navigate(tabs[index].graph) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

@Composable
private fun rememberMainTabs(showAlbumTab: Boolean): ImmutableList<MainTab> =
    remember(showAlbumTab) {
        buildList {
            add(MainTab(BaseTab, R.string.tab_base, Icons.Filled.Home, Icons.Outlined.Home))
            add(MainTab(ShopTab, R.string.tab_shop, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart))
            if (showAlbumTab) {
                add(MainTab(AlbumTab, R.string.tab_album, Icons.Filled.Collections, Icons.Outlined.Collections))
            }
            add(MainTab(FriendsTab, R.string.tab_friends, Icons.Filled.Group, Icons.Outlined.Group))
            add(MainTab(MoreTab, R.string.tab_more, Icons.Filled.Settings, Icons.Outlined.Settings))
        }.toImmutableList()
    }

@Composable
private fun MainNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BaseTab,
        modifier = Modifier.fillMaxSize(),
    ) {
        baseGraph(navController)
        shopGraph()
        albumGraph()
        friendsGraph(navController)
        moreGraph(navController)
    }
}

private fun NavGraphBuilder.baseGraph(
    navController: NavHostController,
) {
    navigation<BaseTab>(startDestination = Base) {
        composable<Base> {
            BaseScreen(
                onNavigateToFriends = { navController.navigate(FriendsTab) },
                onNavigateToShop = { navController.navigate(ShopTab) },
            )
        }
        composable<RaidVisit> {
            // friendGuestId arrives via SavedStateHandle in RaidVisitViewModel.
            RaidVisitRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavGraphBuilder.shopGraph() {
    navigation<ShopTab>(startDestination = Shop) {
        composable<Shop> { ShopRoute() }
    }
}

private fun NavGraphBuilder.albumGraph() {
    navigation<AlbumTab>(startDestination = Album) {
        composable<Album> { AlbumRoute() }
    }
}

private fun NavGraphBuilder.friendsGraph(
    navController: NavHostController,
) {
    navigation<FriendsTab>(startDestination = Friends) {
        composable<Friends> {
            FriendsRoute(
                onNavigateToRaidVisit = { friendGuestId ->
                    navController.navigate(RaidVisit(friendGuestId))
                },
            )
        }
    }
}

private fun NavGraphBuilder.moreGraph(
    navController: NavHostController,
) {
    navigation<MoreTab>(startDestination = Settings) {
        composable<Settings> {
            SettingsRoute(
                onForParents = { navController.navigate(ForParents) },
                onPosterImport = { navController.navigate(PosterImport) },
            )
        }
        composable<ForParents> {
            ForParentsRoute(onBack = { navController.popBackStack() })
        }
        composable<PosterImport> {
            PosterRoute(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun BoxScope.MainTabBar(
    tabs: ImmutableList<MainTab>,
    destination: NavDestination?,
    onTabSelected: (Int) -> Unit,
) {
    val tabItems = tabs.map { tab ->
        TabItem(
            label = stringResource(tab.labelRes),
            selectedIcon = tab.selectedIcon,
            unselectedIcon = tab.unselectedIcon,
        )
    }.toImmutableList()
    val hierarchy = destination?.let { dest ->
        generateSequence(dest) { it.parent }.toList()
    }.orEmpty()
    val selectedIndex = tabs.indexOfFirst { tab ->
        val routeClass = when (tab.graph) {
            is BaseTab -> BaseTab::class
            is ShopTab -> ShopTab::class
            is AlbumTab -> AlbumTab::class
            is FriendsTab -> FriendsTab::class
            else -> MoreTab::class
        }
        hierarchy.any { it.hasRoute(routeClass) }
    }.coerceAtLeast(0)
    GlassTabBar(
        tabs = tabItems,
        selectedIndex = selectedIndex,
        onTabSelected = onTabSelected,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = AppDimens.screenMargin,
                end = AppDimens.screenMargin,
                bottom = AppDimens.screenMargin,
            ),
    )
}
