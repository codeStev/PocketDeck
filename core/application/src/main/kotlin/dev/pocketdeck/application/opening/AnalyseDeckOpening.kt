package dev.pocketdeck.application.opening

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.opening.OpeningAnalyser
import dev.pocketdeck.domain.opening.OpeningAnalysis
import dev.pocketdeck.domain.opening.TurnOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * Inbound port: how likely each Pokémon of a deck is to be attacking in the first turns, for the player who starts and
 * for the one who goes second. It simulates thousands of starts, so it can take a moment.
 */
fun interface AnalyseDeckOpening {
    suspend operator fun invoke(deck: Deck): OpeningAnalysis
}

class AnalyseDeckOpeningService(
    private val catalogs: CardCatalogRepository,
    /** Where the simulation runs: it is heavy, so not on the main thread. */
    private val context: CoroutineContext = Dispatchers.Default,
) : AnalyseDeckOpening {
    override suspend fun invoke(deck: Deck): OpeningAnalysis {
        val catalog = catalogs.current()
        // Seeded by the deck itself: the same deck always shows the same odds, instead of numbers that jitter on every look.
        val seed = deck.hashCode()
        return withContext(context) {
            // The two seats do not depend on each other, so they use two cores; on a phone this halves the wait.
            val first = async { OpeningAnalyser(Random(seed)).analyse(deck, catalog, TurnOrder.FIRST) }
            val second = async { OpeningAnalyser(Random(seed + 1)).analyse(deck, catalog, TurnOrder.SECOND) }
            OpeningAnalysis(first.await(), second.await())
        }
    }
}
