package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.Deck

/** The cards a deck can be made from: one print per card, as many copies as the player owns, at most two per name. */
internal class OwnedCards(
    catalog: CardCatalog,
    private val inventory: Inventory,
) {
    private val distinct: List<Card> = catalog.cards.distinctBy { it.gameplayKey }.filter { inventory.copiesOf(it) > 0 }

    val pokemon: List<PokemonCard> = distinct.filterIsInstance<PokemonCard>()
    val trainers: List<TrainerCard> = distinct.filterIsInstance<TrainerCard>()

    private val pokemonByName = pokemon.groupBy { it.name }

    fun limitOf(card: Card): Int = minOf(inventory.copiesOf(card), Deck.MAX_COPIES_PER_NAME)

    /** The owned Pokémon that [card] evolves from; the sturdiest one if the player owns several of that name. */
    fun predecessorOf(card: PokemonCard): PokemonCard? =
        card.evolvesFrom?.let { pokemonByName[it]?.maxByOrNull(PokemonCard::hp) }
}
