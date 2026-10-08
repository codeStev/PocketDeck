package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.TrainerCard

/**
 * Recognises the hand-moving trainers from the English card text. A text it does not fully understand gives null, so
 * the odds err on the careful side instead of crediting a card with something it might not do.
 */
object HandEffectReader {
    /** What Copycat draws on average: it counts the opponent's hand, which we cannot see. */
    const val ASSUMED_OPPONENT_HAND = 4

    private val draw = Regex("^draw (\\d+|a) cards?$")
    private val search =
        Regex(
            "^(flip a coin\\. if heads, |flip a coin until you get tails\\. for each heads, )?" +
                "put (\\d+|a|an) random (.+) from your deck into your hand$",
        )
    private val evolveFromDeck =
        Regex(
            "^choose 1 of your (.+?) in play(?: with a maximum hp of (\\d+) or less)?\\. " +
                "put a random (.+?) from your deck that evolves from that pokemon onto that pokemon to evolve it" +
                "(?:\\. you can't use this card.*)?$",
        )
    private val lookAtTop =
        Regex(
            "^look at the top (\\d+) cards of your deck\\. put all (.+) you find there into your hand\\. shuffle the other cards back into your deck$",
        )
    private val eachPlayerShuffles =
        Regex("^each player shuffles the cards in their hand into their deck, then draws that many cards$")
    private val copycat =
        Regex("^shuffle your hand into your deck\\. draw a card for each card in your opponent's hand$")

    fun of(card: Card): OpeningEffect? = (card as? TrainerCard)?.effect?.let(::read)

    fun read(text: String): OpeningEffect? {
        val normalized =
            text
                .replace('é', 'e')
                .replace('’', '\'')
                .trim()
                .trimEnd('.')
        val plain = normalized.lowercase()
        return readDraw(plain) ?: readSearch(normalized, plain) ?: readLookAtTop(normalized, plain)
            ?: readEvolveFromDeck(normalized, plain) ?: readOther(plain)
    }

    private fun readDraw(plain: String) =
        draw.matchEntire(plain)?.let { OpeningEffect.Draw(countOf(it.groupValues[1])) }

    private fun readSearch(
        original: String,
        plain: String,
    ): OpeningEffect? {
        val match = search.matchEntire(plain) ?: return null
        val phrase = original.substring(match.groups[3]!!.range).removePrefix(AMONG)
        val filter = CardFilterReader.read(phrase) ?: return null
        return OpeningEffect.Search(countOf(match.groupValues[2]), filter, coinOf(match.groupValues[1]))
    }

    private fun coinOf(prefix: String) =
        when {
            prefix.isEmpty() -> CoinRule.NONE
            prefix.contains("until") -> CoinRule.UNTIL_TAILS
            else -> CoinRule.FLIP_ONCE
        }

    private fun readEvolveFromDeck(
        original: String,
        plain: String,
    ): OpeningEffect? {
        val match = evolveFromDeck.matchEntire(plain) ?: return null
        val base = SlotFilterReader.read(original.substring(match.groups[1]!!.range)) ?: return null
        val evolution = CardFilterReader.read(original.substring(match.groups[3]!!.range)) ?: return null
        val maxHp = match.groupValues[2].toIntOrNull()
        val target =
            SlotFilter {
                card,
                isActive,
                ->
                base.matches(card, isActive) && (maxHp == null || card.hp <= maxHp)
            }
        return OpeningEffect.EvolveFromDeck(target, evolution)
    }

    private fun readLookAtTop(
        original: String,
        plain: String,
    ): OpeningEffect? {
        val match = lookAtTop.matchEntire(plain) ?: return null
        val filter = CardFilterReader.read(original.substring(match.groups[2]!!.range)) ?: return null
        return OpeningEffect.LookAtTop(match.groupValues[1].toInt(), filter)
    }

    private fun readOther(plain: String): OpeningEffect? =
        when {
            plain.startsWith(RARE_CANDY_START) -> OpeningEffect.RareCandy
            eachPlayerShuffles.matches(plain) -> OpeningEffect.ShuffleHandAndDraw(redrawCount = null)
            copycat.matches(plain) -> OpeningEffect.ShuffleHandAndDraw(ASSUMED_OPPONENT_HAND)
            else -> null
        }

    private const val RARE_CANDY_START =
        "choose 1 of your basic pokemon in play. if you have a stage 2 card in your hand that evolves from " +
            "that pokemon, put that card onto the basic pokemon to evolve it, skipping the stage 1"

    private fun countOf(word: String) = word.toIntOrNull() ?: 1

    private const val AMONG = "cards from among "
}
