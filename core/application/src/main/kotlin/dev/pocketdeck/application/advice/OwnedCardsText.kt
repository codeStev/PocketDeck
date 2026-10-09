package dev.pocketdeck.application.advice

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.deck.Deck

/**
 * The player's cards as an advisor reads them. The list is most of what a request costs (about 22,000 tokens for a big
 * collection), so it is written compactly without leaving out anything the advisor could use: the columns are explained
 * once instead of labelled on every line, an effect text that several cards share is written once and referred to, and
 * evolutions the player cannot play (they own nothing to evolve from) are left out.
 */
internal object OwnedCardsText {
    private const val MAX_EFFECT_LENGTH = 160
    private const val SHARED_EFFECT_MIN_LENGTH = 30
    private const val RARE_CANDY = "Rare Candy"

    private const val LEGEND =
        "Cards the player owns. Pokémon: id | name | type stage (from <what it evolves from>) | " +
            "<hp>hp r<retreat cost> x<copies you may use> | " +
            "attacks as cost damage name: effect, separated by ; | ability. " +
            "Trainers: id | name | kind | x<copies you may use> | effect."
    private const val REFERENCES =
        " An effect written like [E1] stands for the text of that name in the list of effects."

    /** The owned cards that can be in a legal deck: one print per card, evolutions only with something to evolve from. */
    fun playable(request: AdviceRequest): List<Card> {
        val owned =
            request.catalog.cards
                .distinctBy { it.gameplayKey }
                .filter { request.inventory.copiesOf(it) > 0 }
        val pokemonNames = owned.filterIsInstance<PokemonCard>().groupBy { it.name }
        val hasRareCandy = owned.any { it is TrainerCard && it.name.value.equals(RARE_CANDY, ignoreCase = true) }

        fun canEvolve(card: PokemonCard): Boolean =
            card.evolvesFrom == null ||
                pokemonNames[card.evolvesFrom].orEmpty().any(::canEvolve) ||
                (card.stage == Stage.STAGE_2 && hasRareCandy)
        return owned.filter { it !is PokemonCard || canEvolve(it) }
    }

    fun write(request: AdviceRequest): String {
        val cards = playable(request)
        val shared = sharedEffects(cards)
        val definitions = shared.entries.joinToString("\n") { (text, code) -> "$code = $text" }
        val lines = cards.map { line(it, minOf(request.inventory.copiesOf(it), Deck.MAX_COPIES_PER_NAME), shared) }
        val legend = if (shared.isEmpty()) LEGEND else LEGEND + REFERENCES
        val effects = if (shared.isEmpty()) "" else "List of effects:\n$definitions\n\n"
        return "$legend\n\n$effects" + lines.joinToString("\n")
    }

    /** Effects on several cards and long enough for a reference to pay off, numbered as they first appear. */
    private fun sharedEffects(cards: List<Card>): Map<String, String> {
        val counts = linkedMapOf<String, Int>()
        cards.flatMap(::effectsOf).forEach { counts.merge(it, 1, Int::plus) }
        val shared = counts.filter { (text, count) -> count > 1 && text.length >= SHARED_EFFECT_MIN_LENGTH }.keys
        return shared.withIndex().associate { (index, text) -> text to "[E${index + 1}]" }
    }

    private fun effectsOf(card: Card): List<String> =
        when (card) {
            is PokemonCard -> card.attacks.mapNotNull { it.effect } + listOfNotNull(card.ability?.effect)
            is TrainerCard -> listOfNotNull(card.effect)
        }.map(::shortened)

    private fun shortened(text: String) =
        if (text.length >
            MAX_EFFECT_LENGTH
        ) {
            text.take(MAX_EFFECT_LENGTH) + "…"
        } else {
            text
        }

    private fun line(
        card: Card,
        copies: Int,
        shared: Map<String, String>,
    ): String =
        when (card) {
            is PokemonCard -> pokemonLine(card, copies, shared)

            is TrainerCard -> "${card.id.value} | ${card.name.value} | ${kind(
                card,
            )} | x$copies | ${effect(card.effect, shared)}"
        }

    private fun kind(card: TrainerCard) =
        card.kind.name
            .lowercase()
            .replaceFirstChar(Char::uppercase)

    private fun pokemonLine(
        card: PokemonCard,
        copies: Int,
        shared: Map<String, String>,
    ): String {
        val type = AdviceFormat.energyName(card.energyType)
        val stage = stageName(card.stage) + (card.evolvesFrom?.let { " (from ${it.value})" } ?: "")
        val attacks =
            card.attacks.joinToString("; ") { attack ->
                "${attack.cost} ${attack.damage ?: "-"} ${attack.name}" +
                    (attack.effect?.let { ": ${effect(it, shared)}" } ?: "")
            }
        val ability = card.ability?.let { " | ability ${it.name}: ${effect(it.effect, shared)}" }.orEmpty()
        val stats = "${card.hp}hp r${card.retreatCost} x$copies"
        return "${card.id.value} | ${card.name.value} | $type $stage | $stats | $attacks$ability"
    }

    private fun stageName(stage: Stage) =
        when (stage) {
            Stage.BASIC -> "Basic"
            Stage.STAGE_1 -> "Stage 1"
            Stage.STAGE_2 -> "Stage 2"
        }

    private fun effect(
        text: String?,
        shared: Map<String, String>,
    ): String = text.orEmpty().let(::shortened).let { shared[it] ?: it }
}
