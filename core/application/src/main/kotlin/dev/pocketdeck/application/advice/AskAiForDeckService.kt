package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.meta.MetaSnapshotRepository
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.DeckValidator
import dev.pocketdeck.domain.deck.DeckViolation
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.deck.TrainerPlayability

/**
 * Asks an advisor for a deck and does not take its word for it: every answer is checked against the cards that exist,
 * the cards the player owns and the rules of the game. What is wrong is sent back, so the advisor can correct itself;
 * after a few tries without a valid deck the player is told so instead of being shown a deck that cannot be played.
 */
class AskAiForDeckService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val snapshots: MetaSnapshotRepository,
    private val advisor: DeckAdvisor,
    private val validator: DeckValidator = DeckValidator(),
    private val completeness: CompletenessCalculator = CompletenessCalculator(),
    private val diagnostics: AdvisorDiagnostics = AdvisorDiagnostics.NONE,
) : AskAiForDeck {
    override suspend fun invoke(wish: String?): Outcome<DeckSuggestion, AskAiFailure> {
        val catalog = catalogs.current()
        val inventory = Inventory.of(collections.current(), catalog)
        val meta = snapshots.current()
        var feedback = emptyList<AdviceFeedback>()
        diagnostics.note(
            "Asking for a deck (wish: ${wish ?: "none"}); the player owns ${catalog.cards.count {
                inventory.copiesOf(
                    it,
                ) > 0
            }} cards",
        )
        repeat(MAX_ATTEMPTS) { attempt ->
            diagnostics.note("Attempt ${attempt + 1} of $MAX_ATTEMPTS")
            val advice =
                when (
                    val outcome =
                        advisor.advise(
                            AdviceRequest(
                                wish?.takeIf {
                                    it.isNotBlank()
                                },
                                catalog,
                                inventory,
                                meta,
                                feedback,
                            ),
                        )
                ) {
                    is Outcome.Failure -> return Outcome.Failure(AskAiFailure.AdvisorFailed(outcome.reason))
                    is Outcome.Success -> outcome.value
                }
            when (val checked = check(advice, catalog, inventory)) {
                is Checked.Valid -> {
                    diagnostics.note("The deck \"${advice.title}\" is valid")
                    return Outcome.Success(suggestionOf(advice, checked.deck, inventory))
                }

                is Checked.Invalid -> {
                    diagnostics.note("The deck was rejected: ${checked.problems.joinToString("; ")}")
                    feedback = feedback + AdviceFeedback(advice, checked.problems)
                }
            }
        }
        return Outcome.Failure(AskAiFailure.NoValidDeck(feedback.last().problems))
    }

    private sealed interface Checked {
        data class Valid(
            val deck: Deck,
        ) : Checked

        data class Invalid(
            val problems: List<String>,
        ) : Checked
    }

    private fun check(
        advice: DeckAdvice,
        catalog: CardCatalog,
        inventory: Inventory,
    ): Checked {
        val resolved = advice.entries.map { it to resolve(it, catalog, inventory) }
        val unknown =
            resolved.filter { it.second == null }.map { (entry, _) ->
                unknownProblem(entry, catalog, inventory)
            }
        if (unknown.isNotEmpty()) return Checked.Invalid(unknown.distinct())
        val entries =
            resolved
                .groupBy({ requireNotNull(it.second) }, { it.first.copies })
                .map { (card, copies) -> DeckEntry(card, copies.sum()) }
        val deck = Deck(entries, advice.energyTypes.toSet())
        val problems =
            notOwned(deck, inventory) + energyProblems(advice) + validator.validate(deck).map(::describe) +
                deadTrainers(deck, catalog)
        return if (problems.isEmpty()) Checked.Valid(deck) else Checked.Invalid(problems)
    }

    /** The card by its id, or else by its name among the cards the player owns; null when neither names a card. */
    private fun resolve(
        entry: AdvisedCard,
        catalog: CardCatalog,
        inventory: Inventory,
    ): Card? {
        catalog[CardId(entry.id)]?.let { return it }
        val name = entry.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return catalog.cards.firstOrNull { it.name.value.equals(name, ignoreCase = true) && inventory.copiesOf(it) > 0 }
    }

    private fun unknownProblem(
        entry: AdvisedCard,
        catalog: CardCatalog,
        inventory: Inventory,
    ): String {
        val number = entry.id.substringAfterLast('-')
        val similar =
            catalog.cards.firstOrNull {
                it.id.value.substringAfterLast('-') == number &&
                    inventory.copiesOf(it) > 0
            }
        val hint = similar?.let { "; did you mean ${it.id.value} (${it.name.value})?" }.orEmpty()
        return "${entry.id} is not a card that exists$hint Use the ids from the list of owned cards exactly"
    }

    private fun notOwned(
        deck: Deck,
        inventory: Inventory,
    ): List<String> =
        deck.entries
            .groupBy { it.card.gameplayKey }
            .mapNotNull { (_, entries) ->
                val card = entries.first().card
                val used = entries.sumOf { it.copies }
                val owned = inventory.copiesOf(card)
                "${card.name.value}: the player owns ${copies(owned)} but the deck uses $used".takeIf { used > owned }
            }

    /** A trainer that talks about Water Pokémon or "Team Rocket" does nothing in a deck without them. */
    private fun deadTrainers(
        deck: Deck,
        catalog: CardCatalog,
    ): List<String> {
        val playability = TrainerPlayability(catalog)
        return deck.entries.map { it.card }.filterIsInstance<TrainerCard>().mapNotNull { trainer ->
            val unmet = playability.unmet(trainer, deck)
            "${trainer.name.value} cannot do anything in this deck; it needs ${unmet.joinToString(
                " and ",
            ) { it.describe() }}"
                .takeIf { unmet.isNotEmpty() }
        }
    }

    private fun copies(count: Int) = if (count == 1) "1 copy" else "$count copies"

    private fun energyProblems(advice: DeckAdvice): List<String> {
        val unusable = advice.energyTypes.filter { it == EnergyType.DRAGON || it == EnergyType.COLORLESS }
        return unusable.map {
            "${it.name.lowercase().replaceFirstChar(Char::uppercase)} energy cannot be chosen for the Energy Zone"
        }
    }

    private fun describe(violation: DeckViolation): String =
        when (violation) {
            is DeckViolation.WrongSize -> {
                "The deck has ${violation.actual} cards but must have exactly ${Deck.SIZE}"
            }

            is DeckViolation.TooManyCopies -> {
                "${violation.name.value} is in the deck ${violation.copies} times, " +
                    "at most ${Deck.MAX_COPIES_PER_NAME} per name"
            }

            DeckViolation.NoBasicPokemon -> {
                "The deck needs at least one Basic Pokémon"
            }

            is DeckViolation.MissingEvolutionPrerequisite -> {
                "${violation.evolution.value} needs ${violation.requires.value} in the deck"
            }

            is DeckViolation.InvalidEnergyZone -> {
                "The Energy Zone needs 1 to ${Deck.MAX_ENERGY_TYPES} energy types, not ${violation.typeCount}"
            }
        }

    private fun suggestionOf(
        advice: DeckAdvice,
        deck: Deck,
        inventory: Inventory,
    ) = DeckSuggestion(
        title = advice.title,
        origin = SuggestionOrigin.AI,
        deck = deck,
        completeness = completeness.calculate(deck, inventory),
        score = AI_SCORE,
        rationale = advice.rationale.takeIf { it.isNotBlank() },
    )

    private companion object {
        const val MAX_ATTEMPTS = 3

        /** Asked for by the player, so it is shown first; the order among such decks is the order they were asked in. */
        const val AI_SCORE = 1.0
    }
}
