package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.rememberHapticTick
import kotlinx.collections.immutable.ImmutableList

/**
 * Floating frosted-glass tab bar. One glass layer per screen, floating over
 * content, never hidden. Filled icon when selected, outline when not;
 * 11sp labels; light haptic tick on every switch.
 */
@Composable
fun GlassTabBar(
    tabs: ImmutableList<TabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val tick = rememberHapticTick()
    Row(
        modifier =
            modifier
                .height(AppDimens.tabBarHeight)
                .clip(AppShapes.capsule)
                .background(colors.glassFill)
                .border(AppDimens.hairline, colors.glassBorder, AppShapes.capsule)
                .padding(horizontal = AppDimens.spacing8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AppShapes.capsule)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                tick()
                                onTabSelected(index)
                            },
                        ).semantics(mergeDescendants = true) {
                            contentDescription = tab.label
                        }.padding(vertical = AppDimens.spacing4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.secondaryLabel,
                    modifier = Modifier.size(AppDimens.iconSize),
                )
                Text(
                    text = tab.label,
                    style = AppTypography.tabLabel,
                    color = if (selected) colors.accent else colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
