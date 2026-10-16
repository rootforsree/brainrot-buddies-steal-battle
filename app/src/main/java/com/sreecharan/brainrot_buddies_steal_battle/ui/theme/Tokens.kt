package com.sreecharan.brainrot_buddies_steal_battle.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fixed palette. Dynamic color is OFF on purpose: it is the single biggest
 * source of muddy palettes. Dark theme is designed, never inverted.
 */
@Immutable
data class AppColors(
    val accent: Color,
    val groupedBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val cardSurface: Color,
    val destructive: Color,
    val success: Color,
    val warning: Color,
    val glassFill: Color,
    val glassBorder: Color,
)

val LightAppColors = AppColors(
    accent = Color(0xFF007AFF),
    groupedBackground = Color(0xFFF2F2F7),
    label = Color(0xFF1D1D1F),
    secondaryLabel = Color(0xFF48484A),
    cardSurface = Color(0xFFFFFFFF),
    destructive = Color(0xFFFF3B30),
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    glassFill = Color(0x66FFFFFF),
    glassBorder = Color(0x33FFFFFF),
)

val DarkAppColors = AppColors(
    accent = Color(0xFF007AFF),
    groupedBackground = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99FFFFFF),
    cardSurface = Color(0xFF2C2C2E),
    destructive = Color(0xFFFF3B30),
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    glassFill = Color(0x26FFFFFF),
    glassBorder = Color(0x1AFFFFFF),
)

object AppTypography {
    val largeTitle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 41.sp,
    )
    val title2 = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
    )
    val headline = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    )
    val body = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 23.sp,
    )
    val subheadline = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )
    val footnote = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )
    val caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    )
    val tabLabel = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 13.sp,
    )
}

object AppShapes {
    val card = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val capsule = CircleShape
}

object AppDimens {
    val spacing4: Dp = 4.dp
    val spacing8: Dp = 8.dp
    val spacing12: Dp = 12.dp
    val spacing16: Dp = 16.dp
    val spacing24: Dp = 24.dp
    val spacing32: Dp = 32.dp
    val screenMargin: Dp = 16.dp
    val rowMinHeight: Dp = 48.dp
    val rowHorizontalPadding: Dp = 16.dp
    val rowVerticalPadding: Dp = 8.dp
    val tabBarHeight: Dp = 56.dp
    val tabBarClearance: Dp = 88.dp
    val iconSize: Dp = 24.dp
    val emptyStateIcon: Dp = 64.dp
    val hairline: Dp = 1.dp
}
