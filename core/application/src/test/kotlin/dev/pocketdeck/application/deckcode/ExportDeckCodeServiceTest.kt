package dev.pocketdeck.application.deckcode

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExportDeckCodeServiceTest {
    private fun pokemon(
        entityId: Int?,
        name: String = "Pokemon $entityId",
    ) = PokemonCard(
        id = CardId("p-$entityId"),
        name = CardName(name),
        energyType = EnergyType.LIGHTNING,
        stage = Stage.BASIC,
        hp = 60,
        gameEntityId = entityId,
    )

    private fun trainer(
        entityId: Int?,
        name: String = "Trainer $entityId",
    ) = TrainerCard(CardId("t-$entityId"), CardName(name), TrainerKind.ITEM, gameEntityId = entityId)

    private fun deck(
        vararg entries: Pair<dev.pocketdeck.domain.card.Card, Int>,
        energy: Set<EnergyType> = setOf(EnergyType.LIGHTNING),
    ) = Deck(entries.map { (card, copies) -> DeckEntry(card, copies) }, energy)

    /** A deck captured from the game (see the community project tcgp-deck-qr), in the order of its code. */
    private val knownDeck =
        deck(
            trainer(40) to 2,
            trainer(1410) to 1,
            trainer(990) to 1,
            trainer(320) to 1,
            trainer(1000) to 1,
            trainer(1030) to 1,
            trainer(170) to 1,
            trainer(30) to 2,
            trainer(290) to 1,
            pokemon(13290) to 2,
            pokemon(980) to 2,
            pokemon(13310) to 1,
            pokemon(18340) to 2,
            pokemon(7410) to 2,
        )

    private val knownCode =
        "C5iWqJiWqJicApiaXpiXwJiaaJiahpiXKpiWnpiWnpiXogkAM+oAM+oAA9QAA9QAM/4AR6QAR6QAHPIAHPIBBA=="

    @Test
    fun `writes the same code the game shows for the same deck`() {
        val outcome = ExportDeckCodeService()(knownDeck)

        outcome shouldBe Outcome.Success(DeckCode(knownCode))
    }

    @Test
    fun `lists every copy of a card on its own`() {
        val code = ExportDeckCodeService()(deck(pokemon(5) to 2, energy = setOf(EnergyType.FIRE)))

        // 0 trainers, 2 pokemon of 3 bytes each, 1 energy (Fire = 2): 00 | 02 000005 000005 | 01 02
        (code as Outcome.Success).value.bytes() shouldBe
            byteArrayOf(0, 2, 0, 0, 5, 0, 0, 5, 1, 2).toList()
    }

    @Test
    fun `trainer ids are shifted so they cannot be mistaken for pokemon`() {
        val code = ExportDeckCodeService()(deck(trainer(40) to 1))

        (code as Outcome.Success).value.bytes().subList(1, 4) shouldBe
            byteArrayOf(0x98.toByte(), 0x96.toByte(), 0xA8.toByte()).toList()
    }

    @Test
    fun `keeps the energy in the order of the deck`() {
        val code =
            ExportDeckCodeService()(
                deck(
                    pokemon(1) to 1,
                    energy = linkedSetOf(EnergyType.PSYCHIC, EnergyType.FIGHTING, EnergyType.DARKNESS),
                ),
            )

        (code as Outcome.Success).value.bytes().takeLast(4) shouldBe listOf<Byte>(3, 5, 6, 7)
    }

    @Test
    fun `names the cards the game does not know`() {
        val outcome = ExportDeckCodeService()(deck(pokemon(null, "Mystery") to 1))

        outcome shouldBe Outcome.Failure(DeckCodeFailure.UnknownToGame(listOf(CardName("Mystery"))))
    }

    @Test
    fun `refuses an energy the game cannot select`() {
        val outcome = ExportDeckCodeService()(deck(pokemon(1) to 1, energy = setOf(EnergyType.DRAGON)))

        outcome shouldBe Outcome.Failure(DeckCodeFailure.NoSelectableEnergy)
    }

    @Test
    fun `leaves out an unselectable energy but keeps the others`() {
        val code =
            ExportDeckCodeService()(
                deck(pokemon(1) to 1, energy = linkedSetOf(EnergyType.WATER, EnergyType.COLORLESS)),
            )

        (code as Outcome.Success).value.bytes().takeLast(2) shouldBe listOf<Byte>(1, 3)
    }
}
