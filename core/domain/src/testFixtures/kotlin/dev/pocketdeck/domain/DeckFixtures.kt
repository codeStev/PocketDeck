package dev.pocketdeck.domain

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry

fun deckOf(
    vararg entries: Pair<Card, Int>,
    energyTypes: Set<EnergyType> = setOf(EnergyType.FIRE),
) = Deck(entries.map { (card, copies) -> DeckEntry(card, copies) }, energyTypes)

val charmander = basicPokemon("Charmander")
val charmeleon = evolvedPokemon("Charmeleon", evolvesFrom = "Charmander")
val charizardEx = evolvedPokemon("Charizard ex", evolvesFrom = "Charmeleon", stage = Stage.STAGE_2)
val magmar = basicPokemon("Magmar")
val pokeBall = trainer("Poké Ball")
val potion = trainer("Potion")
val rareCandy = trainer("Rare Candy")
val professorsResearch = trainer("Professor's Research", TrainerKind.SUPPORTER)
val sabrina = trainer("Sabrina", TrainerKind.SUPPORTER)
val giovanni = trainer("Giovanni", TrainerKind.SUPPORTER)

/** A legal 20-card deck: every entry has two copies. */
fun validFireDeck(): Deck =
    deckOf(
        charmander to 2,
        charmeleon to 2,
        charizardEx to 2,
        magmar to 2,
        pokeBall to 2,
        potion to 2,
        rareCandy to 2,
        professorsResearch to 2,
        sabrina to 2,
        giovanni to 2,
    )
