package com.sreecharan.brainrot_buddies_steal_battle.navigation

import kotlinx.serialization.Serializable

/**
 * Frozen §7 routes. This is the complete set: no new routes without a
 * contract amendment. Screens owned by other builders are referenced from
 * AppNavHost (see the integration notes there).
 */
@Serializable
object Base

@Serializable
object Shop

@Serializable
object Album

@Serializable
object Friends

@Serializable
object Settings

@Serializable
data class RaidVisit(val friendGuestId: String)

@Serializable
object ForParents

@Serializable
object PosterImport

/**
 * Per-tab nested navigation graphs (skill: one nested graph per feature tab,
 * with saveState/restoreState on switches). These are graph markers, not
 * screens: the §7 screen set above is unchanged.
 */
@Serializable
internal object BaseTab

@Serializable
internal object ShopTab

@Serializable
internal object AlbumTab

@Serializable
internal object FriendsTab

@Serializable
internal object MoreTab
