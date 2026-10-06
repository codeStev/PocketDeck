package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome

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
) : DeckAdvisor {
    override suspend fun advise(request: AdviceRequest): Outcome<DeckAdvice, AdvisorFailure> {
        val current = settings.current()
        val usable = current.usable().filter { it in providers }
        if (usable.isEmpty()) return Outcome.Failure(AdvisorFailure.MissingKey)
        var firstFailure: AdvisorFailure? = null
        for (provider in usable) {
            val access = ProviderAccess(requireNotNull(current.keyOf(provider)), current.modelOf(provider))
            when (val outcome = providers.getValue(provider).advise(request, access)) {
                is Outcome.Success -> return outcome
                is Outcome.Failure -> firstFailure = firstFailure ?: outcome.reason
            }
        }
        return Outcome.Failure(requireNotNull(firstFailure))
    }
}
