package com.sreecharan.brainrot_buddies_steal_battle.ui.album

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 10 "coming soon" silhouettes: 6 Legendary + 4 Mythic (§1). Names are
 * unknown in v1, so they render as "???" silhouettes.
 */
@Immutable
data class ComingSoonBuddy(
    val kind: String,
)

val COMING_SOON: ImmutableList<ComingSoonBuddy> =
    (
        List(6) { ComingSoonBuddy("Legendary") } +
            List(4) { ComingSoonBuddy("Mythic") }
    ).toImmutableList()

@Immutable
data class Sticker(
    val buddyId: String,
    val name: String,
    val caught: Boolean,
    val blurb: String,
    val voiceFile: String,
)

@Immutable
data class TierBonusState(
    val rarity: Rarity,
    val caught: Int,
    val total: Int,
    val amount: Long,
    val claimable: Boolean,
    val claimed: Boolean,
)

@Immutable
data class AlbumUiState(
    val isLoading: Boolean = true,
    val commons: ImmutableList<Sticker> = persistentListOf(),
    val rares: ImmutableList<Sticker> = persistentListOf(),
    val epics: ImmutableList<Sticker> = persistentListOf(),
    val comingSoon: ImmutableList<ComingSoonBuddy> = COMING_SOON,
    val bonuses: ImmutableList<TierBonusState> = persistentListOf(),
)

sealed interface AlbumEvent {
    data class BonusClaimed(
        val amount: Long,
    ) : AlbumEvent

    data object ClaimFailed : AlbumEvent
}

/**
 * Tier-bonus claimed state lives in [GamePrefs] (string set); the coin
 * reward comes from [GameEngine.albumTierReward].
 */
@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        private val repository: GameRepository,
        private val gamePrefs: GamePrefs,
        private val engine: GameEngine,
        private val soundManager: SoundManager,
    ) : ViewModel() {
        private val eventsChannel = Channel<AlbumEvent>(Channel.BUFFERED)
        val events = eventsChannel.receiveAsFlow()

        val uiState: StateFlow<AlbumUiState> =
            combine(
                repository.observeOwnedBuddies(),
                gamePrefs.claimedAlbumBonuses,
            ) { buddiesResult, claimed ->
                val buddies = buddiesResult.dataOrNull().orEmpty()
                val caughtIds = buddies.map { it.buddyId }.toSet()

                fun stickers(rarity: Rarity): ImmutableList<Sticker> =
                    BuddyCatalog.all
                        .filter { it.rarity == rarity }
                        .map { def ->
                            Sticker(
                                buddyId = def.id.value,
                                name = def.name,
                                caught = def.id.value in caughtIds,
                                blurb = def.blurb,
                                voiceFile = def.voiceFile,
                            )
                        }.toImmutableList()

                val commons = stickers(Rarity.COMMON)
                val rares = stickers(Rarity.RARE)
                val epics = stickers(Rarity.EPIC)
                AlbumUiState(
                    isLoading = false,
                    commons = commons,
                    rares = rares,
                    epics = epics,
                    bonuses =
                        listOf(
                            bonusState(Rarity.COMMON, commons, claimed),
                            bonusState(Rarity.RARE, rares, claimed),
                            bonusState(Rarity.EPIC, epics, claimed),
                        ).toImmutableList(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AlbumUiState(),
            )

        private fun bonusState(
            rarity: Rarity,
            stickers: ImmutableList<Sticker>,
            claimed: Set<String>,
        ): TierBonusState {
            val caught = stickers.count { it.caught }
            val key = rarity.name
            return TierBonusState(
                rarity = rarity,
                caught = caught,
                total = stickers.size,
                amount = engine.albumTierReward(rarity),
                claimable = caught == stickers.size && key !in claimed,
                claimed = key in claimed,
            )
        }

        /** Tap a caught sticker: hear its catchphrase. */
        fun onStickerTapped(sticker: Sticker) {
            if (!sticker.caught) return
            soundManager.playVoice(sticker.voiceFile)
        }

        fun onClaimBonus(rarity: Rarity) {
            viewModelScope.launch {
                val buddies =
                    repository.observeOwnedBuddies().awaitData()
                        ?: return@launch eventsChannel.send(AlbumEvent.ClaimFailed)
                val claimed = gamePrefs.claimedAlbumBonuses.first()
                val key = rarity.name
                if (key in claimed) {
                    eventsChannel.send(AlbumEvent.ClaimFailed)
                    return@launch
                }
                val caughtIds = buddies.map { it.buddyId }.toSet()
                val complete =
                    BuddyCatalog.all
                        .filter { it.rarity == rarity }
                        .all { it.id.value in caughtIds }
                if (!complete) {
                    eventsChannel.send(AlbumEvent.ClaimFailed)
                    return@launch
                }
                val vault =
                    repository.observeVault().awaitData()
                        ?: return@launch eventsChannel.send(AlbumEvent.ClaimFailed)
                val amount = engine.albumTierReward(rarity)
                repository.upsertVault(
                    vault.copy(
                        coins = vault.coins + amount,
                        lifetimeCoins = vault.lifetimeCoins + amount,
                    ),
                )
                gamePrefs.addClaimedAlbumBonus(key)
                eventsChannel.send(AlbumEvent.BonusClaimed(amount))
            }
        }
    }

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

private suspend fun <T> Flow<Result<T>>.awaitData(): T? =
    when (val result = first { it !is Result.Loading }) {
        is Result.Success -> result.data
        else -> null
    }
