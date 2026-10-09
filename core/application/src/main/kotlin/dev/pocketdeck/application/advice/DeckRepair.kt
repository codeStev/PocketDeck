package dev.pocketdeck.application.advice

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry
import dev.pocketdeck.domain.deck.DeckValidator
import dev.pocketdeck.domain.deck.TrainerPlayability

/**
 * Turns an advisor's answer that asks for more than the player owns into a deck they can build: copies are cut down to what
 * is owned (and to the limit per name), and a deck that is then too small is filled with owned cards that fit it. Advisors
 * like to copy tournament lists, which hold cards the player may not have; this keeps that from ending in an error.
 * Returns null when no legal deck comes out of it.
 */
class DeckRepair(
    private val validator: DeckValidator = DeckValidator(),
) {
    fun repair(
        wanted: List<Pair<Card, Int>>,
        energyTypes: List<EnergyType>,
        catalog: CardCatalog,
        inventory: Inventory,
    ): Deck? {
        val energy = energyTypes.filter { it in AdviceFormat.selectableEnergy }.toSet()
        if (energy.isEmpty()) return null
        val copies = clamped(wanted, inventory)
        val cards =
            copies.entries
                .flatMap { (card, count) -> List(count) { card } }
                .take(Deck.SIZE)
                .toMutableList()
        fill(cards, energy, catalog, inventory)
        val deck = deckOf(cards, energy)
        return deck.takeIf { validator.validate(it).isEmpty() && deadTrainers(it, catalog) }
    }

    private fun deckOf(
        cards: List<Card>,
        energy: Set<EnergyType>,
    ) = Deck(cards.groupingBy { it }.eachCount().map { (card, count) -> DeckEntry(card, count) }, energy)

    /** The cards of the advice with copies cut to what the player owns; prints of one card share the owned copies. */
    private fun clamped(
        wanted: List<Pair<Card, Int>>,
        inventory: Inventory,
    ): Map<Card, Int> {
        val used = mutableMapOf<GameplayKey, Int>()
        val result = linkedMapOf<Card, Int>()
        for ((card, count) in wanted) {
            val room =
                minOf(inventory.copiesOf(card), Deck.MAX_COPIES_PER_NAME) - used.getOrDefault(card.gameplayKey, 0)
            val taken = minOf(count, room)
            if (taken > 0) {
                used[card.gameplayKey] = used.getOrDefault(card.gameplayKey, 0) + taken
                result[card] = result.getOrDefault(card, 0) + taken
            }
        }
        return result
    }

    private fun fill(
        deck: MutableList<Card>,
        energy: Set<EnergyType>,
        catalog: CardCatalog,
        inventory: Inventory,
    ) {
        val playability = TrainerPlayability(catalog)
        val owned = catalog.cards.distinctBy { it.gameplayKey }.filter { inventory.copiesOf(it) > 0 }
        for (card in owned.sortedBy { rank(it, deck, energy) }) {
            while (deck.size < Deck.SIZE && room(card, deck, inventory) > 0 && fits(card, deck, energy, playability)) {
                deck += card
            }
        }
    }

    private fun room(
        card: Card,
        deck: List<Card>,
        inventory: Inventory,
    ) = minOf(inventory.copiesOf(card), Deck.MAX_COPIES_PER_NAME) - deck.count { it.gameplayKey == card.gameplayKey }

    /** Copies of what the deck already plays first, then Basic Pokémon of its energy, then trainers, then the rest. */
    private fun rank(
        card: Card,
        deck: List<Card>,
        energy: Set<EnergyType>,
    ): Int =
        when {
            deck.any { it.gameplayKey == card.gameplayKey } -> 0
            card is PokemonCard && card.stage == Stage.BASIC && card.energyType in energy -> 1
            card is TrainerCard -> 2
            else -> 3
        }

    /** A Pokémon must match the deck's energy and, when it evolves, have what it evolves from in the deck. */
    private fun fits(
        card: Card,
        deck: List<Card>,
        energy: Set<EnergyType>,
        playability: TrainerPlayability,
    ): Boolean =
        when (card) {
            is PokemonCard -> {
                card.energyType in energy &&
                    (card.evolvesFrom == null || deck.any { it.name == card.evolvesFrom })
            }

            is TrainerCard -> {
                playability.unmet(card, deckOf(deck, energy)).isEmpty()
            }
        }

    private fun deadTrainers(
        deck: Deck,
        catalog: CardCatalog,
    ): Boolean {
        val playability = TrainerPlayability(catalog)
        return deck.entries.map { it.card }.filterIsInstance<TrainerCard>().all {
            playability
                .unmet(
                    it,
                    deck,
                ).isEmpty()
        }
    }
}
