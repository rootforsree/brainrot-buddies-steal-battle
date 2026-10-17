package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Buddy sprite poses, contract § Asset Spec (Charan 2026-10-16).
 *
 * Every buddy ships 3 poses in drawable-nodpi: `<slug>.webp` (normal),
 * `<slug>_happy.webp` (claim celebration), `<slug>_sad.webp` (steal/stolen).
 * Pose swaps layer on top of code-driven motion; they never replace it.
 */
enum class BuddyPose(
    val suffix: String,
) {
    NORMAL(""),
    HAPPY("_happy"),
    SAD("_sad"),
}

/** Drawable name for a buddy's pose, e.g. "buddy_frogboot_happy". */
fun buddyPoseImageName(
    buddyId: String,
    pose: BuddyPose,
): String = "buddy_$buddyId${pose.suffix}"

/**
 * Resource id for a buddy's pose, or 0 when the pose drawable is missing
 * (falls back to the normal pose at the call site).
 */
fun Context.buddyPoseResId(
    buddyId: String,
    pose: BuddyPose,
): Int = resources.getIdentifier(buddyPoseImageName(buddyId, pose), "drawable", packageName)

/**
 * Loads a buddy pose image by buddy id, falling back to the normal pose and
 * then to null when the drawables are missing.
 */
@Composable
fun rememberBuddyPoseImage(
    buddyId: String,
    pose: BuddyPose,
): ImageBitmap? {
    val context = LocalContext.current
    return remember(buddyId, pose) {
        val poseRes = context.buddyPoseResId(buddyId, pose)
        val resId = if (poseRes != 0) poseRes else context.buddyPoseResId(buddyId, BuddyPose.NORMAL)
        if (resId == 0) {
            null
        } else {
            runCatching {
                BitmapFactory
                    .decodeResource(context.resources, resId)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
}
