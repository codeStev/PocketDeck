package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard

/**
 * Recognises, in the English text of a trainer or an ability, the effects that attach energy, switch the Active Pokémon
 * or change the Retreat Cost. A text it does not fully understand gives null.
 */
object BoardEffectReader {
    private const val COUNT = "(an?|\\d+)"
    private const val TYPE = "\\[ ?([a-z]) ?]"
    private val sources =
        listOf(
            EnergySource.ENERGY_ZONE to "take $COUNT $TYPE energy from your energy zone and attach it to ",
            EnergySource.DISCARD_PILE to "attach $COUNT (?:$TYPE )?energy from your discard pile to ",
        )
    private val switchOwn =
        Regex("^(?:once during your turn, you may )?switch your active (.+?) with 1 of your benched pokemon$")
    private val bearerComesIn =
        Regex("^once during your turn, if this pokemon is on your bench, you may switch it with your active pokemon$")
    private val retreatDiscount = Regex("^during this turn, the retreat cost of your active pokemon is (\\d+) less$")
    private const val FREE_RETREAT = "your active pokemon has no retreat cost"
    private const val THIS_POKEMON = "this pokemon"

    fun of(card: Card): OpeningEffect? =
        when (card) {
            is TrainerCard -> card.effect
            is PokemonCard -> card.ability?.effect
        }?.let(::read)

    fun read(text: String): OpeningEffect? {
        val plain =
            text
                .replace('é', 'e')
                .replace('’', '\'')
                .trim()
                .trimEnd('.')
                .lowercase()
        return sources.firstNotNullOfOrNull { (source, phrase) -> readAttach(plain, source, phrase) }
            ?: readMove(plain)
            ?: readSwitch(plain)
            ?: readRetreat(plain)
    }

    private fun readAttach(
        plain: String,
        source: EnergySource,
        phrase: String,
    ): OpeningEffect? {
        val shapes =
            listOf<Pair<Regex, (List<String>) -> OpeningEffect?>>(
                Regex(
                    "^choose 1 of your (.+?), and flip a coin until you get tails\\. for each heads, ${phrase}that pokemon$",
                ) to
                    { g -> attach(source, g[2], g[3], SlotFilterReader.read(g[1]), coin = CoinRule.UNTIL_TAILS) },
                Regex("^choose 1 of your (.+?)\\. ${phrase}that pokemon(\\. your turn ends)?$") to
                    { g -> attach(source, g[2], g[3], SlotFilterReader.read(g[1]), endsTurn = g[4].isNotEmpty()) },
                Regex("^(flip a coin\\. if heads, )?$phrase(.+)$") to
                    { g -> attach(source, g[2], g[3], targetOf(g[4]), coin = flip(g[1]), self = g[4] == THIS_POKEMON) },
                Regex(
                    "^once during your turn, (if this pokemon is (?:in the active spot|on your bench), )?you may $phrase(.+?)(?:\\. if you (?:use this ability|do),.*)?$",
                ) to
                    { g -> abilityAttach(source, g) },
                Regex(
                    "^once during your turn, when you play this pokemon from your hand to evolve 1 of your pokemon, you may $phrase(.+)$",
                ) to
                    { g -> attach(source, g[1], g[2], targetOf(g[3]), trigger = AbilityTrigger.ON_EVOLVE) },
                Regex("^at the end of your first turn, $phrase(.+)$") to
                    { g ->
                        attach(
                            source,
                            g[1],
                            g[2],
                            targetOf(g[3]),
                            trigger = AbilityTrigger.END_OF_FIRST_TURN,
                            self = true,
                        )
                    },
            )
        return shapes.firstNotNullOfOrNull { (pattern, build) ->
            pattern.matchEntire(plain)?.let { build(it.groupValues) }
        }
    }

    private fun abilityAttach(
        source: EnergySource,
        g: List<String>,
    ): OpeningEffect? =
        attach(
            source,
            g[2],
            g[3],
            targetOf(g[4]),
            endsTurn = g[0].contains("your turn ends"),
            self = g[4] == THIS_POKEMON,
            placement = placementOf(g[1]),
        )

    private fun placementOf(condition: String) =
        when {
            condition.contains("active spot") -> Placement.ACTIVE
            condition.contains("bench") -> Placement.BENCH
            else -> Placement.ANYWHERE
        }

    private fun flip(group: String) = if (group.isEmpty()) CoinRule.NONE else CoinRule.FLIP_ONCE

    private fun attach(
        source: EnergySource,
        count: String,
        letter: String,
        target: SlotFilter?,
        coin: CoinRule = CoinRule.NONE,
        endsTurn: Boolean = false,
        trigger: AbilityTrigger = AbilityTrigger.ON_DEMAND,
        self: Boolean = false,
        placement: Placement = Placement.ANYWHERE,
    ): OpeningEffect? {
        val energy =
            letter.firstOrNull()?.let { symbol ->
                EnergyType.entries.firstOrNull {
                    it.symbol ==
                        symbol.uppercaseChar()
                }
            }
        if (letter.isNotEmpty() && energy == null) return null
        if (energy == null && source == EnergySource.ENERGY_ZONE) return null
        if (target == null && !self) return null
        return OpeningEffect.AttachEnergy(
            energy = energy,
            count = count.toIntOrNull() ?: 1,
            source = source,
            coin = coin,
            target = target.takeUnless { self },
            endsTurn = endsTurn,
            trigger = trigger,
            bearerPlacement = placement,
        )
    }

    private fun targetOf(phrase: String): SlotFilter? =
        if (phrase ==
            THIS_POKEMON
        ) {
            null
        } else {
            SlotFilterReader.read(phrase)
        }

    private val move =
        Regex(
            "^(?:(once during your turn|as often as you like during your turn), you may )?" +
                "move (.+?) energy from (.+?) to (.+)$",
        )
    private val typeSymbol = Regex("\\[ ?([a-z]) ?]")

    /** "Move all [ L ] Energy from your Benched Pokémon to your Raichu in the Active Spot", "...from each of your Pokémon to this Pokémon". */
    private fun readMove(plain: String): OpeningEffect? {
        val match = move.matchEntire(plain) ?: return null
        val (_, amount, source, destination) =
            match.destructured.let {
                listOf(match.groupValues[1], it.component2(), it.component3(), it.component4())
            }
        val types = typeSymbol.findAll(amount).mapNotNull { symbolType(it.groupValues[1]) }.toSet()
        val each = source.startsWith("each of your") || source == "your benched pokemon"
        val from = SlotFilterReader.read(source.replace("each of your", "your")) ?: return null
        val to = if (destination == THIS_POKEMON) null else SlotFilterReader.read(destination) ?: return null
        return OpeningEffect.MoveEnergy(
            types = types.ifEmpty { null },
            count = if (amount.startsWith("all")) null else countOfWord(amount.substringBefore(' ')),
            from = from,
            to = to,
            fromEach = each,
            repeatable = match.groupValues[1].startsWith("as often"),
        )
    }

    private fun countOfWord(word: String) = word.toIntOrNull() ?: 1

    private fun symbolType(letter: String) = EnergyType.entries.firstOrNull { it.symbol == letter.uppercase()[0] }

    private fun readSwitch(plain: String): OpeningEffect? {
        if (bearerComesIn.matches(plain)) {
            return OpeningEffect.SwitchOwn(
                SlotFilter {
                    _,
                    _,
                    ->
                    true
                },
                bearerComesIn = true,
            )
        }
        val active = switchOwn.matchEntire(plain)?.groupValues?.get(1) ?: return null
        val filter = CardFilterReader.read(active) ?: return null
        return OpeningEffect.SwitchOwn(SlotFilter { card, isActive -> isActive && filter.matches(card) })
    }

    private fun readRetreat(plain: String): OpeningEffect? =
        retreatDiscount.matchEntire(plain)?.let { OpeningEffect.RetreatDiscount(it.groupValues[1].toInt()) }
            ?: OpeningEffect.FreeRetreat.takeIf { plain == FREE_RETREAT }
}
