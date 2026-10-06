package dev.pocketdeck.adapter.carddb

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.SourceUnavailable
import dev.pocketdeck.domain.card.ArtworkUrl
import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class MasterCatalogSourceTest {
    private val sample: String =
        requireNotNull(javaClass.getResource("/master-sample.json")) { "master-sample.json missing" }.readText()

    private fun sourceOf(json: String) = MasterCatalogSource { ByteArrayInputStream(json.toByteArray()) }

    private suspend fun cards(json: String = sample): Map<String, Card> =
        (sourceOf(json).fetchAll() as Outcome.Success).value.associateBy { it.id.value }

    private suspend fun failureOf(json: String) = (sourceOf(json).fetchAll() as Outcome.Failure).reason.reason

    /** Corrupts the first occurrence of [original] in the sample dataset and returns the resulting failure. */
    private suspend fun failureWhenReplacing(
        original: String,
        corrupted: String,
    ): String {
        check(original in sample) { "fixture does not contain $original" }
        return failureOf(sample.replaceFirst(original, corrupted))
    }

    @Test
    fun `reads every card of the dataset`() =
        runTest {
            cards().size shouldBe 14
        }

    @Test
    fun `maps a basic pokemon`() =
        runTest {
            val bulbasaur = cards().getValue("a1-001") as PokemonCard

            bulbasaur.id shouldBe CardId("a1-001")
            bulbasaur.name shouldBe CardName("Bulbasaur")
            bulbasaur.energyType shouldBe EnergyType.GRASS
            bulbasaur.stage shouldBe Stage.BASIC
            bulbasaur.evolvesFrom shouldBe null
        }

    @Test
    fun `carries the number the game itself uses for the card`() =
        runTest {
            cards().getValue("a1-001").gameEntityId shouldBe 10
        }

    @Test
    fun `carries the attacks of a pokemon with their cost damage and effect`() =
        runTest {
            val vineWhip = (cards().getValue("a1-001") as PokemonCard).attacks.single()

            vineWhip shouldBe Attack("Vine Whip", "GC", 40, null)
        }

    @Test
    fun `carries the effect text of a trainer`() =
        runTest {
            (cards().getValue("a1-219") as TrainerCard).effect shouldBe "Heal 50 damage from 1 of your [ G ] Pokémon."
        }

    @Test
    fun `maps evolutions with their predecessor`() =
        runTest {
            (cards().getValue("a1-002") as PokemonCard).run {
                stage shouldBe Stage.STAGE_1
                evolvesFrom shouldBe CardName("Bulbasaur")
            }
            (cards().getValue("a1-003") as PokemonCard).stage shouldBe Stage.STAGE_2
        }

    @Test
    fun `recognises ex pokemon`() =
        runTest {
            (cards().getValue("a1-004") as PokemonCard).isEx shouldBe true
        }

    @Test
    fun `maps every trainer kind`() =
        runTest {
            (cards().getValue("a2-147") as TrainerCard).kind shouldBe TrainerKind.TOOL
            (cards().getValue("a1-219") as TrainerCard).kind shouldBe TrainerKind.SUPPORTER
            (cards().getValue("b2-153") as TrainerCard).kind shouldBe TrainerKind.STADIUM
            (cards().getValue("a1-216") as TrainerCard).kind shouldBe TrainerKind.ITEM
        }

    @Test
    fun `carries english and german names`() =
        runTest {
            val charizard = cards().getValue("a2b-108")

            charizard.nameIn(Language.ENGLISH) shouldBe CardName("Charizard ex")
            charizard.nameIn(Language.GERMAN) shouldBe CardName("Glurak ex")
            cards().getValue("b3a-100").nameIn(Language.GERMAN) shouldBe CardName("Klibbe")
        }

    @Test
    fun `prefers the german artwork and falls back to the english one`() =
        runTest {
            (cards().getValue("a1-001").artwork!!.value).shouldContain("/de/")
            val withoutGerman = cards().getValue("b4-203").artwork!!.value
            withoutGerman shouldBe ArtworkUrl(withoutGerman).value
        }

    @Test
    fun `same-named pokemon with different attacks are different cards`() =
        runTest {
            val cards = cards()

            cards.getValue("a1-094").gameplayKey shouldNotBe cards.getValue("a1a-025").gameplayKey
            cards.getValue("a1-094").gameplayKey.value shouldStartWith "Pikachu#"
        }

    @Test
    fun `a missing dataset is reported as unavailable`() =
        runTest {
            val source = MasterCatalogSource { throw IOException("asset not found") }

            source.fetchAll() shouldBe Outcome.Failure(SourceUnavailable("asset not found"))
        }

    @Test
    fun `malformed json is reported as unavailable`() =
        runTest {
            failureOf("{ nope") shouldContain "unreadable"
        }

    @Test
    fun `a dataset from a newer schema is rejected`() =
        runTest {
            failureOf(sample.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 99")) shouldContain
                "schema version 99"
        }

    @Test
    fun `an unknown energy type is reported and names the card`() =
        runTest {
            failureOf(sample.replaceFirst("\"energyType\": \"grass\"", "\"energyType\": \"cosmic\"")) shouldContain
                "a1-001"
        }

    @Test
    fun `an unknown trainer kind is reported`() =
        runTest {
            failureOf(
                sample.replaceFirst("\"trainerKind\": \"supporter\"", "\"trainerKind\": \"gadget\""),
            ) shouldContain
                "a1-219"
        }

    @Test
    fun `a pokemon without hp is reported`() =
        runTest {
            failureWhenReplacing("\"hp\": 70", "\"hp\": null") shouldContain "hp"
        }
}
