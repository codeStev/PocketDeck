package dev.pocketdeck.adapter.ui.chase

import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.meta.ChaseEntry
import dev.pocketdeck.domain.meta.ChasePack
import dev.pocketdeck.domain.meta.ChaseRanking

object ChaseListing {
    /** A dialog is no place for a long list: the best few cards and packs say it. */
    const val MAX_CARDS = 10
    const val MAX_PACKS = 5
    const val PACK_PREVIEW_CARDS = 3

    fun build(
        ranking: ChaseRanking,
        language: Language,
    ): ChaseUiState =
        ChaseUiState(
            cards = ranking.cards.take(MAX_CARDS).map { cardItem(it, language) },
            packs = ranking.packs.take(MAX_PACKS).map { packItem(it, language) },
        )

    private fun cardItem(
        entry: ChaseEntry,
        language: Language,
    ) = ChaseCardItem(
        key = entry.card.gameplayKey.value,
        name = entry.card.nameIn(language).value,
        artworkUrl = entry.card.artwork?.value,
        decksFinished = entry.decksFinished,
        decksHelped = entry.decksHelped,
        packs = entry.packs,
    )

    private fun packItem(
        pack: ChasePack,
        language: Language,
    ) = ChasePackItem(
        name = pack.name,
        cardCount = pack.cards.size,
        cardNames = pack.cards.take(PACK_PREVIEW_CARDS).map { it.card.nameIn(language).value },
    )
}
