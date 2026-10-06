package dev.pocketdeck.application.advice

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.meta.Archetype
import dev.pocketdeck.domain.meta.CardPopularity
import dev.pocketdeck.domain.meta.MetaSnapshot

/** What an advisor is told, in plain text; every provider gets the same words and puts them into its own format. */
data class AdvicePrompt(
    /** The rules and how to answer. Never changes between requests. */
    val system: String,
    /** The player's cards. Long, and the same for every request until the collection changes. */
    val collection: String,
    /** What wins tournaments right now. */
    val tournament: String,
    /** What is asked this time. */
    val task: String,
)

object AdvicePromptWriter {
    private const val MAX_EFFECT_LENGTH = 160
    private const val ARCHETYPES_SHOWN = 8
    private const val POPULAR_CARDS_SHOWN = 20
    private const val PERCENT = 100

    fun write(request: AdviceRequest): AdvicePrompt =
        AdvicePrompt(
            system = system(),
            collection = collection(request),
            tournament = tournament(request),
            task = task(request.wish),
        )

    private fun system(): String =
        """
        You are an expert Pokémon TCG Pocket deck builder. Build one competitive deck from the cards the player owns.

        Rules of the game that your deck must follow:
        - The deck has exactly ${Deck.SIZE} cards.
        - At most ${Deck.MAX_COPIES_PER_NAME} copies of cards with the same name.
        - At least one Basic Pokémon. Every evolution needs the Pokémon it evolves from in the deck.
        - The deck chooses 1 to ${Deck.MAX_ENERGY_TYPES} energy types from: ${AdviceFormat.selectableEnergy.joinToString {
            AdviceFormat
                .energyName(
                    it,
                )
        }}.
          Attacks are paid with these (colorless cost, written C, can be paid with any energy).

        Rules for your answer:
        - Use only cards from the list of owned cards. Give each card with its exact id from the list and its name, and never
          more copies than listed. Copy the ids character by character; do not shorten or guess them.
        - Every trainer must have something to act on in your deck. A trainer that heals or boosts [W] Pokémon needs Water
          Pokémon (and Water energy), one that searches for Pokémon with "Team Rocket" in their name needs such Pokémon,
          one that names Pokémon needs at least one of them, and one that talks about Stage 2 or Mega Pokémon needs those.
          Never include a trainer whose condition the deck cannot meet.
        - Prefer cards that work together and a clear plan; use the tournament picture as evidence of what is strong.
        - Call the tool ${AdviceFormat.TOOL_NAME} exactly once with the finished deck. Do not answer in any other way.
        """.trimIndent()

    private fun collection(request: AdviceRequest): String {
        val owned =
            request.catalog.cards
                .distinctBy { it.gameplayKey }
                .filter { request.inventory.copiesOf(it) > 0 }
        val lines = owned.map { card -> line(card, minOf(request.inventory.copiesOf(card), Deck.MAX_COPIES_PER_NAME)) }
        return "Cards the player owns (id | name | details | copies you may use | effects):\n" +
            lines.joinToString("\n")
    }

    private fun line(
        card: Card,
        copies: Int,
    ): String =
        when (card) {
            is PokemonCard -> pokemonLine(card, copies)

            is TrainerCard -> "${card.id.value} | ${card.name.value} | ${kind(
                card,
            )} | copies $copies | ${effect(card.effect)}"
        }

    private fun kind(card: TrainerCard) =
        card.kind.name
            .lowercase()
            .replaceFirstChar(Char::uppercase)

    private fun pokemonLine(
        card: PokemonCard,
        copies: Int,
    ): String {
        val stage = stageName(card.stage) + (card.evolvesFrom?.let { " (evolves from ${it.value})" } ?: "")
        val attacks =
            card.attacks.joinToString("; ") { attack ->
                "${attack.cost} ${attack.damage ?: "-"} ${attack.name}" +
                    (attack.effect?.let { ": ${effect(it)}" } ?: "")
            }
        val ability = card.ability?.let { " | ability ${it.name}: ${effect(it.effect)}" }.orEmpty()
        return "${card.id.value} | ${card.name.value} | ${typeName(card)} $stage | HP ${card.hp} | " +
            "retreat ${card.retreatCost} | copies $copies | attacks: $attacks$ability"
    }

    private fun typeName(card: PokemonCard) = AdviceFormat.energyName(card.energyType)

    private fun stageName(stage: Stage) =
        when (stage) {
            Stage.BASIC -> "Basic"
            Stage.STAGE_1 -> "Stage 1"
            Stage.STAGE_2 -> "Stage 2"
        }

    private fun effect(text: String?): String =
        (text ?: "").let {
            if (it.length >
                MAX_EFFECT_LENGTH
            ) {
                it.take(MAX_EFFECT_LENGTH) + "…"
            } else {
                it
            }
        }

    private fun tournament(request: AdviceRequest): String {
        val archetypes =
            request.meta.archetypes
                .sortedByDescending { it.strength }
                .take(ARCHETYPES_SHOWN)
        if (archetypes.isEmpty()) return "No tournament data is available; rely on card strength and synergy."
        return "Strong decks in recent tournaments (name, share of the field, win rate, a typical list):\n" +
            archetypes.joinToString("\n") { archetypeLine(it, request) } +
            "\n\n" + popularCards(request)
    }

    private fun archetypeLine(
        archetype: Archetype,
        request: AdviceRequest,
    ): String {
        val list = archetype.decklists.first()
        val cards =
            list.entries.joinToString(", ") {
                "${it.copies} ${request.catalog[it.cardId]?.name?.value ?: it.cardId.value}"
            }
        return "- ${archetype.name.value} (${percent(
            archetype.share,
        )}% of decks, ${percent(archetype.winRate)}% wins): $cards"
    }

    private fun popularCards(request: AdviceRequest): String {
        val popularity = CardPopularity.of(request.meta, request.catalog)
        val owned =
            request.catalog.cards
                .distinctBy { it.gameplayKey }
                .filter { request.inventory.copiesOf(it) > 0 }
        val top =
            owned.sortedByDescending { popularity.of(it) }.take(POPULAR_CARDS_SHOWN).filter {
                popularity.of(it) >
                    0
            }
        return "Cards the player owns that many tournament decks play: " +
            top.joinToString(", ") { "${it.name.value} (${percent(popularity.of(it))}%)" }
    }

    private fun percent(share: Double) = Math.round(share * PERCENT)

    private fun task(wish: String?): String =
        if (wish == null) {
            "Build the best deck you can from these cards."
        } else {
            "Build the best deck you can from these cards. The player asks for: $wish"
        }
}

/** The advisor's earlier answer was rejected; this is what it is told so it can correct itself. */
fun AdviceFeedback.describe(): String =
    "The deck was rejected for these reasons:\n" + problems.joinToString("\n") { "- $it" } +
        "\nCall ${AdviceFormat.TOOL_NAME} again with a corrected deck that fixes all of them."
