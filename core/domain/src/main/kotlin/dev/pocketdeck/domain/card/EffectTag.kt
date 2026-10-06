package dev.pocketdeck.domain.card

/** What a card's effect does, in broad terms; two cards sharing tags play a similar role in a deck. */
enum class EffectTag {
    DISCARD_OWN_ENERGY,
    DISCARD_OPPONENT_ENERGY,
    ATTACH_ENERGY,
    ATTACH_ENERGY_FROM_ZONE,
    ATTACH_ENERGY_FROM_DISCARD,
    DRAW,
    SEARCH_DECK,
    HEAL,
    SWITCH,
    BENCH_DAMAGE,
    RANDOM_TARGETS,
    DAMAGE_SCALING,
    DAMAGE_REDUCTION,
    SELF_DAMAGE,
    COIN_FLIP,
    POISON,
    BURN,
    SLEEP,
    PARALYZE,
    CONFUSE,
    PREVENT_ACTION,
    MOVE_ENERGY,
    RETREAT_COST,
    SNIPE,
    HAND_DISRUPTION,
    TOOL_REMOVAL,
    RECOVER_FROM_DISCARD,
    COUNTER_DAMAGE,
}

/** Reads the English effect texts of the card dataset; the patterns are tied to how the game words things. */
object EffectClassifier {
    private val rules: List<Pair<EffectTag, Regex>> =
        listOf(
            EffectTag.DISCARD_OWN_ENERGY to "discard [^.]*energy[^.]* from this pok",
            EffectTag.DISCARD_OPPONENT_ENERGY to "discard [^.]*energy[^.]* from your opponent",
            EffectTag.ATTACH_ENERGY to "attach[^.]*energy|energy[^.]*attach",
            EffectTag.ATTACH_ENERGY_FROM_ZONE to "energy zone[^.]*attach|attach[^.]*energy zone",
            EffectTag.ATTACH_ENERGY_FROM_DISCARD to
                "attach[^.]*energy[^.]*from your discard pile|energy from your discard pile[^.]*attach",
            EffectTag.DRAW to "\\bdraw (?:a |an |\\d+ )?cards?\\b",
            EffectTag.SEARCH_DECK to "from your deck",
            EffectTag.HEAL to "\\bheal\\b",
            EffectTag.SWITCH to "\\bswitch\\b",
            EffectTag.BENCH_DAMAGE to "damage to (each|1) of your opponent's benched",
            EffectTag.RANDOM_TARGETS to "chosen at random",
            EffectTag.DAMAGE_SCALING to "for each|\\+\\d+ damage|more damage",
            EffectTag.DAMAGE_REDUCTION to "takes? [-−–]\\d+ damage",
            EffectTag.SELF_DAMAGE to "damage to itself|damage to this pok",
            EffectTag.COIN_FLIP to "\\bflip\\b",
            EffectTag.POISON to "\\bpoisoned\\b",
            EffectTag.BURN to "\\bburned\\b",
            EffectTag.SLEEP to "\\basleep\\b",
            EffectTag.PARALYZE to "\\bparalyzed\\b",
            EffectTag.CONFUSE to "\\bconfused\\b",
            EffectTag.PREVENT_ACTION to "can't attack|can't retreat|prevent all",
            EffectTag.MOVE_ENERGY to "\\bmove [^.]*energy",
            EffectTag.RETREAT_COST to "retreat cost",
            EffectTag.SNIPE to "damage to 1 of your opponent's pok|choose a spot|damage to 1 of your pok",
            EffectTag.HAND_DISRUPTION to "opponent's hand|opponent reveals|into your opponent's deck",
            EffectTag.TOOL_REMOVAL to "discard [^.]*pok[eé]mon tools?[^.]* from your opponent",
            EffectTag.RECOVER_FROM_DISCARD to "(put|take|return)[^.]*from your discard pile",
            EffectTag.COUNTER_DAMAGE to "damage to the attacking pok",
        ).map { (tag, pattern) -> tag to Regex(pattern, RegexOption.IGNORE_CASE) }

    fun tagsOf(text: String): Set<EffectTag> {
        val normalized = text.replace('’', '\'')
        return rules.filter { (_, pattern) -> pattern.containsMatchIn(normalized) }.mapTo(linkedSetOf()) { it.first }
    }

    fun tagsOf(card: Card): Set<EffectTag> = card.effectTexts.flatMapTo(linkedSetOf()) { tagsOf(it) }
}
