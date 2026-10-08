package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard

/**
 * Recognises, in the English text, the effects that keep working after a card is played or a Pokémon is in play:
 * Stadiums, Pokémon Tools that make retreating cheaper, abilities that draw every turn, doubled energy. Like the other
 * readers it gives null for a text it does not fully understand, so the odds never credit a card with a guess.
 */
object PassiveEffectReader {
    private val everyTurn =
        Regex("^at the (beginning|end) of your turn, if this pokemon is (in the active spot|on your bench), (.+)$")
    private val oncePerPlayerTurn = Regex("^once during each player's turn, that player may (.+)$")
    private val drawUpTo =
        Regex("^at the end of each player's turn, that player draws cards until they have (\\d+) cards in their hand$")
    private const val RAINBOW_CAVE =
        "once during each player's turn, that player may discard the energy that has been generated in their " +
            "energy zone. if they do, the next energy is produced"
    private val stadiumRetreat =
        Regex(
            "^the retreat cost of each (\\[ ?[a-z] ?] pokemon) in play \\(both yours and your opponent's\\) is (\\d+) less$",
        )
    private val toolRetreat = Regex("^the retreat cost of the (.+?) this card is attached to is (\\d+) less$")
    private val aura =
        Regex(
            "^as long as this pokemon is on your bench, your active (\\[ ?[a-z] ?] )?pokemon's retreat cost is (\\d+) less$",
        )
    private val doubled =
        Regex(
            "^each \\[ ?([a-z]) ?] energy attached to your \\[ ?([a-z]) ?] pokemon provides 2 \\[ ?([a-z]) ?] energy.*$",
        )
    private const val EEVEE =
        "as long as this pokemon is in the active spot, it can evolve during your first turn or the turn you play it"
    private const val MIRAIDON =
        "once during your turn, when you put this pokemon from your hand onto your bench, you may switch it with " +
            "your active pokemon. if you do, move all of your energy in play to this pokemon"

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
        return readEveryTurn(plain) ?: readStadium(plain) ?: readRetreat(plain) ?: readEnergy(plain)
    }

    private fun readEveryTurn(plain: String): OpeningEffect? {
        val match = everyTurn.matchEntire(plain) ?: return null
        val action = actionOf(match.groupValues[3]) ?: return null
        val moment = if (match.groupValues[1] == "beginning") TurnMoment.START_OF_TURN else TurnMoment.END_OF_TURN
        val placement = if (match.groupValues[2].contains("active")) Placement.ACTIVE else Placement.BENCH
        return OpeningEffect.EveryTurn(moment, action, placement)
    }

    /** Only drawing and searching are credited: the rest of what an ability does is not about the opening. */
    private fun actionOf(phrase: String): OpeningEffect? =
        HandEffectReader.read(phrase.replace("their ", "your ")).takeIf {
            it is OpeningEffect.Draw ||
                it is OpeningEffect.Search
        }

    private fun readStadium(plain: String): OpeningEffect? {
        if (plain == RAINBOW_CAVE) return OpeningEffect.Stadium(StadiumRule.RerollEnergyZone)
        drawUpTo
            .matchEntire(
                plain,
            )?.let { return OpeningEffect.Stadium(StadiumRule.DrawUpTo(it.groupValues[1].toInt())) }
        stadiumRetreat.matchEntire(plain)?.let {
            val filter = CardFilterReader.read(it.groupValues[1]) ?: return null
            return OpeningEffect.Stadium(StadiumRule.LowerRetreat(filter, it.groupValues[2].toInt()))
        }
        val inner = oncePerPlayerTurn.matchEntire(plain)?.groupValues?.get(1) ?: return null
        val action = actionOf(inner) ?: return null
        return OpeningEffect.Stadium(StadiumRule.OncePerTurn(action))
    }

    private fun readRetreat(plain: String): OpeningEffect? {
        toolRetreat.matchEntire(plain)?.let {
            val wearer = CardFilterReader.read(it.groupValues[1]) ?: return null
            return OpeningEffect.RetreatTool(wearer, it.groupValues[2].toInt())
        }
        val match = aura.matchEntire(plain) ?: return null
        val noun = match.groupValues[1] + "pokemon"
        val active = CardFilterReader.read(noun.trim()) ?: return null
        return OpeningEffect.RetreatAura(active, match.groupValues[2].toInt())
    }

    private fun readEnergy(plain: String): OpeningEffect? {
        if (plain == MIRAIDON) return OpeningEffect.SwitchInAndTakeEnergy
        if (plain == EEVEE) return OpeningEffect.EvolvesAtOnce
        val match = doubled.matchEntire(plain) ?: return null
        val letters = match.groupValues.drop(1).toSet()
        if (letters.size != 1) return null
        val type = EnergyType.entries.firstOrNull { it.symbol == letters.single().uppercase()[0] } ?: return null
        return OpeningEffect.DoubleEnergy(type)
    }
}
