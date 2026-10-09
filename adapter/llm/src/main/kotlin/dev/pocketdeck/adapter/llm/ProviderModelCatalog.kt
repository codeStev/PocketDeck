package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorModelCatalog
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.ListedModel

/** Asks the catalog of the provider in question. */
class ProviderModelCatalog(
    private val catalogs: Map<AdvisorProvider, AdvisorModelCatalog>,
) : AdvisorModelCatalog {
    override suspend fun available(
        provider: AdvisorProvider,
        apiKey: String?,
        address: String?,
    ): Outcome<List<ListedModel>, AdvisorFailure> =
        catalogs[provider]?.available(provider, apiKey, address) ?: Outcome.Success(emptyList())
}
