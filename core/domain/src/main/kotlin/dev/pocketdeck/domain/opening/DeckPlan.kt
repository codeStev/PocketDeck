package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.deck.Deck

/** What a simulation needs to know about a deck, worked out once instead of in every simulated game. */
internal class DeckPlan(
    deck: Deck,
    catalog: CardCatalog,
) {
    private val distinct: List<Card> = deck.entries.map { it.card }.distinctBy { it.gameplayKey }

    /** Every card of the deck, one entry per copy. */
    val cards: List<Card> = deck.entries.flatMap { entry -> List(entry.copies) { entry.card } }

    /** One card per Pokémon of the deck; each gets a simulation that plays around it. */
    val pokemon: List<PokemonCard> = distinct.filterIsInstance<PokemonCard>()

    /** The types the Energy Zone can give, in a fixed order so a seeded run repeats. */
    val energyTypes: List<EnergyType> = deck.energyTypes.sortedBy { it.ordinal }

    private val effects: Map<GameplayKey, OpeningEffect?> =
        distinct.associate {
            it.gameplayKey to
                (HandEffectReader.of(it) ?: BoardEffectReader.of(it) ?: PassiveEffectReader.of(it))
        }

    private val discards: Map<Attack, EnergyDiscard?> =
        pokemon.flatMap { it.attacks }.distinct().associateWith(AttackEffectReader::read)

    private val allPokemon: List<PokemonCard> = (catalog.cards + cards).filterIsInstance<PokemonCard>()

    /** Stage 1 name to the Basic it evolves from; Rare Candy needs it to link a Stage 2 to its Basic. */
    private val basicBehind: Map<CardName, CardName> =
        allPokemon
            .filter { it.stage == Stage.STAGE_1 }
            .mapNotNull { stage1 -> stage1.evolvesFrom?.let { stage1.name to it } }
            .toMap()

    private val predecessor: Map<CardName, CardName> =
        allPokemon.mapNotNull { card -> card.evolvesFrom?.let { card.name to it } }.toMap()

    /** What a trainer does, or what a Pokémon's ability does, as far as the opening is concerned. */
    fun effectOf(card: Card): OpeningEffect? = effects[card.gameplayKey]

    fun discardOf(attack: Attack): EnergyDiscard? = discards[attack]

    /** The Basic a Stage 2 card grows from, through its Stage 1; null when the catalog does not know the line. */
    fun basicOf(stage2: PokemonCard): CardName? = stage2.evolvesFrom?.let(basicBehind::get)

    /** The names of [card] and everything it evolves from: a deck built around [card] needs all of them. */
    fun lineOf(card: PokemonCard): Set<CardName> = generateSequence(card.name) { predecessor[it] }.toSet()
}
