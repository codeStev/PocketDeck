package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import kotlin.time.TimeSource

/** What a provider needs to be called on the player's behalf. */
data class ProviderAccess(
    val apiKey: String,
    val model: String,
)

/** Outbound port: one AI service. The player's key and model come with every call, so the service keeps no settings. */
fun interface ProviderDeckAdvisor {
    suspend fun advise(
        request: AdviceRequest,
        access: ProviderAccess,
    ): Outcome<DeckAdvice, AdvisorFailure>
}

/**
 * Asks the providers the player has set up, in the order they chose. A provider that is busy (free models often are),
 * unreachable or gives no deck is skipped for the next one; if none works, the first provider's failure is reported,
 * because that is the one the player expects to work.
 */
class ConfiguredDeckAdvisor(
    private val settings: AdvisorSettingsStore,
    private val providers: Map<AdvisorProvider, ProviderDeckAdvisor>,
    private val diagnostics: AdvisorDiagnostics = AdvisorDiagnostics.NONE,
) : DeckAdvisor {
    override suspend fun advise(request: AdviceRequest): Outcome<DeckAdvice, AdvisorFailure> {
        val current = settings.current()
        val usable = current.usable().filter { it in providers }
        if (usable.isEmpty()) {
            diagnostics.note("No AI service has a key; nothing to ask")
            return Outcome.Failure(AdvisorFailure.MissingKey)
        }
        val prompt = AdvicePromptWriter.write(request)
        val size = prompt.collection.length + prompt.tournament.length
        diagnostics.note(
            "Asking in this order: ${usable.joinToString()}; the prompt has $size characters of cards and tournament " +
                "data, ${request.feedback.size} corrections so far",
        )
        var firstFailure: AdvisorFailure? = null
        for (provider in usable) {
            val access = ProviderAccess(requireNotNull(current.keyOf(provider)), current.modelOf(provider))
            diagnostics.note("Asking $provider with model ${access.model}")
            val started = TimeSource.Monotonic.markNow()
            val outcome = providers.getValue(provider).advise(request, access)
            diagnostics.note(
                "$provider answered after ${started.elapsedNow().inWholeMilliseconds} ms: ${summary(outcome)}",
            )
            when (outcome) {
                is Outcome.Success -> return outcome
                is Outcome.Failure -> firstFailure = firstFailure ?: outcome.reason
            }
        }
        return Outcome.Failure(requireNotNull(firstFailure))
    }

    private fun summary(outcome: Outcome<DeckAdvice, AdvisorFailure>) =
        when (outcome) {
            is Outcome.Success -> "a deck of ${outcome.value.entries.sumOf { it.copies }} cards"
            is Outcome.Failure -> outcome.reason.toString()
        }
}
