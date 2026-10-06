package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.EffectClassifier
import dev.pocketdeck.domain.card.EffectTag
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind

/** Rules of thumb for how much a card is worth in a deck. They rank cards; they do not claim to be exact. */
internal object CardRatings {
    private const val EFFECT_ONLY_DAMAGE = 30.0
    private const val STRENGTH_SCALE = 30.0
    private const val HP_SCALE = 150.0
    private const val HP_WEIGHT = 0.5
    private const val EX_BONUS = 0.2
    private const val ABILITY_BONUS = 0.3
    private const val EXTRA_CARD_PENALTY = 0.1
    private const val MAXIMUM_LINE_RATING = 2.0

    private val usefulAbilities = setOf(EffectTag.ATTACH_ENERGY, EffectTag.DRAW, EffectTag.SEARCH_DECK, EffectTag.HEAL)

    /** Whether the energy of the deck pays for [attack]; colorless cost can be paid with anything. */
    fun payable(
        attack: Attack,
        energy: Set<EnergyType>,
    ): Boolean {
        val symbols = energy.map { it.symbol }.toSet() + EnergyType.COLORLESS.symbol
        return attack.cost.all { it in symbols }
    }

    fun bestAttack(
        card: PokemonCard,
        energy: Set<EnergyType>,
    ): Attack? = card.attacks.filter { payable(it, energy) }.maxByOrNull { strength(it) }

    /** Damage for the energy it costs; an attack that only has an effect counts as a modest one. */
    fun strength(attack: Attack): Double = (attack.damage?.toDouble() ?: EFFECT_ONLY_DAMAGE) / (attack.cost.length + 1)

    /** The energy types an attack needs besides colorless. */
    fun energyOf(attack: Attack): Set<EnergyType> =
        EnergyType.entries.filter { it != EnergyType.COLORLESS && it.symbol in attack.cost }.toSet()

    fun lineRating(
        line: PokemonLine,
        energy: Set<EnergyType>,
    ): Double {
        val card = line.last
        val attack = bestAttack(card, energy)?.let { strength(it) } ?: 0.0
        val sturdiness = card.hp / HP_SCALE * HP_WEIGHT
        val bonus = (if (card.isEx) EX_BONUS else 0.0) + (if (hasUsefulAbility(card)) ABILITY_BONUS else 0.0)
        val cost = (line.chain.size - 1) * EXTRA_CARD_PENALTY
        return (attack / STRENGTH_SCALE + sturdiness + bonus - cost).coerceIn(0.0, MAXIMUM_LINE_RATING)
    }

    private fun hasUsefulAbility(card: PokemonCard) =
        card.ability?.let { EffectClassifier.tagsOf(it.effect).any { tag -> tag in usefulAbilities } } == true

    /** Cards that find other cards, draw, or keep the deck running are worth more than ones that need a situation. */
    fun trainerRating(card: TrainerCard): Double =
        kindWeight(card.kind) + EffectClassifier.tagsOf(card).sumOf { tagWeight(it) }

    private fun kindWeight(kind: TrainerKind) =
        when (kind) {
            TrainerKind.SUPPORTER -> 1.0
            TrainerKind.ITEM -> 0.8
            TrainerKind.TOOL, TrainerKind.STADIUM -> 0.5
        }

    private fun tagWeight(tag: EffectTag) =
        when (tag) {
            EffectTag.SEARCH_DECK, EffectTag.DRAW -> 2.0
            EffectTag.ATTACH_ENERGY -> 1.5
            EffectTag.HEAL, EffectTag.SWITCH, EffectTag.HAND_DISRUPTION -> 1.0
            EffectTag.MOVE_ENERGY -> 0.7
            else -> 0.3
        }
}
