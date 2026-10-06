package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaSnapshot

/** A deck an advisor came up with, before anybody has checked it. */
data class DeckAdvice(
    val title: String,
    val rationale: String,
    val entries: List<DecklistEntry>,
    val energyTypes: List<EnergyType>,
)

/** An earlier answer and what was wrong with it, so the advisor can correct itself. */
data class AdviceFeedback(
    val advice: DeckAdvice,
    val problems: List<String>,
)

/** Everything an advisor may base its deck on. */
data class AdviceRequest(
    /** What the player asked for, in their own words; null leaves it to the advisor. */
    val wish: String?,
    val catalog: CardCatalog,
    val inventory: Inventory,
    val meta: MetaSnapshot,
    val feedback: List<AdviceFeedback> = emptyList(),
)

sealed interface AdvisorFailure {
    /** The player has not given the advisor's service a key yet. */
    data object MissingKey : AdvisorFailure

    data object InvalidKey : AdvisorFailure

    /** The service is overloaded or the player has used up their limit; trying again later may work. */
    data class Busy(
        val reason: String,
    ) : AdvisorFailure

    data class NotReachable(
        val reason: String,
    ) : AdvisorFailure

    /** The service refused the request. */
    data class Rejected(
        val reason: String,
    ) : AdvisorFailure

    /** The service answered, but not with a deck. */
    data class Unusable(
        val reason: String,
    ) : AdvisorFailure
}

/**
 * Outbound port: an advisor (an AI service) that proposes a deck from the player's cards and the tournament picture.
 * Nothing it says is trusted: [AskAiForDeckService] checks every answer against the rules.
 */
fun interface DeckAdvisor {
    suspend fun advise(request: AdviceRequest): Outcome<DeckAdvice, AdvisorFailure>
}
