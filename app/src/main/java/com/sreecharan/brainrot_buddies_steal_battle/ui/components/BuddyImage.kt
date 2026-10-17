package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.sreecharan.brainrot_buddies_steal_battle.R

/**
 * Buddy/hero art by drawable name (e.g. "buddy_frogboot", "hero_benny").
 *
 * Art is owned by the coordinator and may land after code merges, so the
 * lookup is by name at runtime: when the named drawable is missing this
 * falls back to [R.drawable.buddy_placeholder] instead of crashing.
 * Builder B should use this for conveyor/base art too.
 */
@Composable
fun BuddyImage(
    imageName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
) {
    val context = LocalContext.current
    val resId = remember(imageName) {
        context.resources.getIdentifier(imageName, "drawable", context.packageName)
    }
    Image(
        painter = painterResource(id = if (resId != 0) resId else R.drawable.buddy_placeholder),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}
