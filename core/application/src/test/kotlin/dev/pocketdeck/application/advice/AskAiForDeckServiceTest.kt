package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.charmeleon
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.giovanni
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.potion
import dev.pocketdeck.domain.professorsResearch
import dev.pocketdeck.domain.rareCandy
import dev.pocketdeck.domain.sabrina
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AskAiForDeckServiceTest {
    private val cards: List<Card> =
        listOf(
            charmander,
            charmeleon,
            charizardEx,
            magmar,
            pokeBall,
            potion,
            rareCandy,
            professorsResearch,
            sabrina,
            giovanni,
        )
    private val catalog = CardCatalog(cards)

    private fun ownedAll(copies: Int = 2) =
        cards.fold(CardCollection.EMPTY) { collection, card -> collection.withCopies(card.id, copies) }

    private val validAdvice =
        DeckAdvice(
            title = "Charizard Fire",
            rationale = "Fire attackers with trainers that find them.",
            entries = validFireDeck().entries.map { AdvisedCard(it.card.id.value, it.card.name.value, it.copies) },
            energyTypes = listOf(EnergyType.FIRE),
        )

    private class ScriptedAdvisor(
        private val answers: List<Outcome<DeckAdvice, AdvisorFailure>>,
    ) : DeckAdvisor {
        val requests = mutableListOf<AdviceRequest>()

        override suspend fun advise(request: AdviceRequest): Outcome<DeckAdvice, AdvisorFailure> {
            requests += request
            return answers[minOf(requests.size - 1, answers.lastIndex)]
        }
    }

    private fun service(
        advisor: DeckAdvisor,
        owned: CardCollection = ownedAll(),
    ) = AskAiForDeckService(
        InMemoryCardCatalogRepository(catalog),
        InMemoryCollectionRepository(owned),
        InMemoryMetaSnapshotRepository(),
        advisor,
    )

    @Test
    fun `turns a valid answer into a suggestion that says why`() =
        runTest {
            val outcome = service(ScriptedAdvisor(listOf(Outcome.Success(validAdvice)))).invoke("a fire deck")

            val suggestion = (outcome as Outcome.Success).value
            suggestion.origin shouldBe SuggestionOrigin.AI
            suggestion.title shouldBe "Charizard Fire"
            suggestion.rationale shouldBe "Fire attackers with trainers that find them."
            suggestion.deck.size shouldBe 20
            suggestion.completeness.isBuildable shouldBe true
        }

    @Test
    fun `passes the wish and the player's cards to the advisor`() =
        runTest {
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(validAdvice)))

            service(advisor).invoke("something with Charizard")

            advisor.requests.single().wish shouldBe "something with Charizard"
        }

    @Test
    fun `sends back what was wrong and accepts the corrected deck`() =
        runTest {
            val tooBig =
                validAdvice.copy(
                    entries =
                        validAdvice.entries + AdvisedCard(potion.id.value, potion.name.value, 2),
                )
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(tooBig), Outcome.Success(validAdvice)))

            val outcome = service(advisor).invoke(null)

            (outcome is Outcome.Success) shouldBe true
            advisor.requests.size shouldBe 2
            advisor.requests
                .last()
                .feedback
                .single()
                .problems
                .any { "20" in it } shouldBe true
        }

    @Test
    fun `refuses cards the player does not own and says how many they own`() =
        runTest {
            val ownsOneMagmar = ownedAll().withCopies(magmar.id, 1)
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(validAdvice), Outcome.Success(validAdvice)))

            service(advisor, ownsOneMagmar).invoke(null)

            advisor.requests
                .last()
                .feedback
                .first()
                .problems shouldContain
                "Magmar: the player owns 1 copy but the deck uses 2"
        }

    @Test
    fun `refuses a card id that does not exist`() =
        runTest {
            val unknown =
                validAdvice.copy(
                    entries =
                        listOf(AdvisedCard("zz-999", null, 2)),
                )
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(unknown), Outcome.Success(validAdvice)))

            service(advisor).invoke(null)

            advisor.requests
                .last()
                .feedback
                .first()
                .problems
                .any { it.startsWith("zz-999 is not a card that exists") } shouldBe true
        }

    @Test
    fun `refuses an energy type the game cannot select`() =
        runTest {
            val dragon = validAdvice.copy(energyTypes = listOf(EnergyType.DRAGON))
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(dragon), Outcome.Success(validAdvice)))

            service(advisor).invoke(null)

            advisor.requests
                .last()
                .feedback
                .single()
                .problems
                .any { "Dragon" in it } shouldBe true
        }

    @Test
    fun `gives up after three wrong answers when not even the player's own cards make a legal deck`() =
        runTest {
            val wrong = validAdvice.copy(entries = validAdvice.entries.take(2))
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(wrong)))
            val tooLittle = CardCollection.EMPTY.withCopies(charmander.id, 1)

            val outcome = service(advisor, tooLittle).invoke(null)

            (outcome as Outcome.Failure).reason.javaClass shouldBe AskAiFailure.NoValidDeck::class.java
            advisor.requests.size shouldBe 3
        }

    @Test
    fun `builds the deck from the owned cards when the advisor keeps asking for cards the player lacks`() =
        runTest {
            val extras = listOf(basicPokemon("Vulpix"), basicPokemon("Growlithe"))
            val bigger = CardCatalog(cards + extras)
            val owned =
                (cards + extras)
                    .fold(CardCollection.EMPTY) { collection, card -> collection.withCopies(card.id, 2) }
                    .withCopies(magmar.id, 0)
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(validAdvice)))

            val outcome =
                AskAiForDeckService(
                    InMemoryCardCatalogRepository(bigger),
                    InMemoryCollectionRepository(owned),
                    InMemoryMetaSnapshotRepository(),
                    advisor,
                ).invoke(null)

            val suggestion = (outcome as Outcome.Success).value
            advisor.requests.size shouldBe 3
            suggestion.deck.size shouldBe 20
            suggestion.completeness.isBuildable shouldBe true
            suggestion.deck.entries.none { it.card.id == magmar.id } shouldBe true
            suggestion.rationale!! shouldContain "not in your collection"
        }

    @Test
    fun `tells the advisor which owned cards it can use instead`() =
        runTest {
            val ownsOneMagmar = ownedAll().withCopies(magmar.id, 1)
            val withoutPotion = validAdvice.copy(entries = validAdvice.entries.filter { it.id != potion.id.value })
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(withoutPotion), Outcome.Success(validAdvice)))

            service(advisor, ownsOneMagmar).invoke(null)

            advisor.requests
                .last()
                .feedback
                .first()
                .problems
                .any { "Only the player's own cards" in it && "(copies still free)" in it } shouldBe true
        }

    @Test
    fun `reports when the advisor cannot be used`() =
        runTest {
            val advisor = ScriptedAdvisor(listOf(Outcome.Failure(AdvisorFailure.InvalidKey)))

            service(advisor).invoke(null) shouldBe
                Outcome.Failure(AskAiFailure.AdvisorFailed(AdvisorFailure.InvalidKey))
        }

    @Test
    fun `adds up the copies when the same card is listed twice`() =
        runTest {
            val split =
                validAdvice.copy(
                    entries =
                        validAdvice.entries.filter { it.id != potion.id.value } +
                            AdvisedCard(potion.id.value, potion.name.value, 1) +
                            AdvisedCard(potion.id.value, potion.name.value, 1),
                )

            val outcome = service(ScriptedAdvisor(listOf(Outcome.Success(split)))).invoke(null)

            (outcome as Outcome.Success).value.deck.copiesNamed(potion.name) shouldBe 2
        }

    @Test
    fun `sends back a trainer that has nothing to act on in the deck`() =
        runTest {
            val waterHeal =
                dev.pocketdeck.domain.card.TrainerCard(
                    dev.pocketdeck.domain.card
                        .CardId("t-heal"),
                    dev.pocketdeck.domain.card
                        .CardName("Irida"),
                    dev.pocketdeck.domain.card.TrainerKind.SUPPORTER,
                    effect = "Heal 40 damage from each of your Pokémon that has any [ W ] Energy attached.",
                )
            val withHeal = CardCatalog(cards + waterHeal)
            val owned = ownedAll().withCopies(waterHeal.id, 2)
            val swapped =
                validAdvice.copy(
                    entries =
                        validAdvice.entries.filter { it.id != potion.id.value } +
                            AdvisedCard(waterHeal.id.value, waterHeal.name.value, 2),
                )
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(swapped), Outcome.Success(validAdvice)))

            AskAiForDeckService(
                InMemoryCardCatalogRepository(withHeal),
                InMemoryCollectionRepository(owned),
                InMemoryMetaSnapshotRepository(),
                advisor,
            ).invoke(null)

            advisor.requests.last().feedback.single().problems.single().let {
                it shouldContain "Irida"
                it shouldContain "Water energy"
            }
        }

    @Test
    fun `finds the card by its name when the id the advisor wrote does not exist`() =
        runTest {
            val mangled =
                validAdvice.copy(
                    entries =
                        validAdvice.entries.map {
                            if (it.id ==
                                potion.id.value
                            ) {
                                it.copy(id = "zz-373")
                            } else {
                                it
                            }
                        },
                )

            val outcome = service(ScriptedAdvisor(listOf(Outcome.Success(mangled)))).invoke(null)

            (outcome as Outcome.Success).value.deck.copiesNamed(potion.name) shouldBe 2
        }

    @Test
    fun `prefers the id when it exists even if the name differs`() =
        runTest {
            val wrongName =
                validAdvice.copy(
                    entries =
                        validAdvice.entries.map {
                            if (it.id ==
                                potion.id.value
                            ) {
                                it.copy(name = "Nonsense")
                            } else {
                                it
                            }
                        },
                )

            val outcome = service(ScriptedAdvisor(listOf(Outcome.Success(wrongName)))).invoke(null)

            (outcome as Outcome.Success).value.deck.copiesNamed(potion.name) shouldBe 2
        }

    @Test
    fun `suggests the card with the same number when an id is wrong and the name does not help`() =
        runTest {
            val unknownName =
                validAdvice.copy(
                    entries =
                        validAdvice.entries.map {
                            if (it.id ==
                                potion.id.value
                            ) {
                                it.copy(id = "x-${potion.id.value.takeLast(6)}", name = "No Such Card")
                            } else {
                                it
                            }
                        },
                )
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(unknownName), Outcome.Success(validAdvice)))

            service(advisor).invoke(null)

            advisor.requests
                .last()
                .feedback
                .first()
                .problems
                .single() shouldContain "not a card that exists"
        }

    @Test
    fun `does not use a name to get around a card the player does not own`() =
        runTest {
            val ownsNoPotion = ownedAll().withCopies(potion.id, 0)
            val mangled =
                validAdvice.copy(
                    entries =
                        validAdvice.entries.map {
                            if (it.id ==
                                potion.id.value
                            ) {
                                it.copy(id = "zz-1")
                            } else {
                                it
                            }
                        },
                )
            val advisor = ScriptedAdvisor(listOf(Outcome.Success(mangled), Outcome.Success(validAdvice)))

            service(advisor, ownsNoPotion).invoke(null)

            advisor.requests
                .last()
                .feedback
                .first()
                .problems
                .any { "owns" in it || "not a card" in it } shouldBe true
        }
}
