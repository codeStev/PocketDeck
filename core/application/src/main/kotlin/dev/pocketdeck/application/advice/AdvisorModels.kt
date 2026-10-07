package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome

/** Whether a model thinks before it answers; thinking costs time, and some free models never get to the answer. */
enum class Thinking {
    ANSWERS_DIRECTLY,
    THINKS_BY_DEFAULT,
    MUST_THINK,
}

/** A model an AI service offers, as the service lists it. */
data class ListedModel(
    val id: String,
    val name: String,
    val contextTokens: Int,
    val free: Boolean,
    val supportsTools: Boolean,
    val thinking: Thinking,
    /** The day the model is withdrawn, as the service states it, if it does. */
    val expiresOn: String?,
)

/** Outbound port: the models a service currently offers. Free models come and go, so this is asked, not hard-coded. */
fun interface AdvisorModelCatalog {
    suspend fun available(provider: AdvisorProvider): Outcome<List<ListedModel>, AdvisorFailure>
}

/** Inbound port: the models of a provider that suit asking for a deck, best first. */
fun interface ListSuitableModels {
    suspend operator fun invoke(provider: AdvisorProvider): Outcome<List<ListedModel>, AdvisorFailure>
}

/**
 * Free models that take tools and a long prompt (the player's cards and the tournament data are about 30,000 tokens, and
 * the answer needs room). Those that answer directly come first, then those with more room.
 */
class ListSuitableModelsService(
    private val catalog: AdvisorModelCatalog,
) : ListSuitableModels {
    override suspend fun invoke(provider: AdvisorProvider): Outcome<List<ListedModel>, AdvisorFailure> =
        when (val outcome = catalog.available(provider)) {
            is Outcome.Failure -> outcome
            is Outcome.Success -> Outcome.Success(outcome.value.filter(::suits).sortedWith(ORDER))
        }

    /** The `openrouter/free` router is left out: it hands the request to any free model, including ones that never answer. */
    private fun suits(model: ListedModel) =
        model.free && model.supportsTools && model.contextTokens >= MINIMUM_CONTEXT &&
            !model.id.startsWith(ROUTER_PREFIX)

    private companion object {
        const val MINIMUM_CONTEXT = 100_000
        const val ROUTER_PREFIX = "openrouter/"
        val ORDER = compareBy<ListedModel> { it.thinking }.thenByDescending { it.contextTokens }.thenBy { it.id }
    }
}
