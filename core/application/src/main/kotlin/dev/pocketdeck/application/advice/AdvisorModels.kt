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
    /** US dollars per million tokens sent and received, when the service states them. */
    val promptPrice: Double? = null,
    val completionPrice: Double? = null,
) {
    val hasPrice: Boolean get() = promptPrice != null && completionPrice != null
}

/** About what asking for one deck costs with a model: the request is long and the answer short. */
object ModelCost {
    private const val PROMPT_TOKENS = 22_000
    private const val ANSWER_TOKENS = 3_000
    private const val PER_MILLION = 1_000_000.0

    /** US dollars for one deck, 0 for a free model, null when the service states no price. */
    fun perDeck(model: ListedModel): Double? {
        if (model.free) return 0.0
        val prompt = model.promptPrice ?: return null
        val answer = model.completionPrice ?: return null
        return (prompt * PROMPT_TOKENS + answer * ANSWER_TOKENS) / PER_MILLION
    }
}

/**
 * What the model picker asks for. [apiKey] is the key as the player typed it, saved or not: Anthropic lists its models
 * only to a key holder. [includePaid] also offers the paid models of a service that has free ones.
 */
data class ModelRequest(
    val provider: AdvisorProvider,
    val apiKey: String? = null,
    val includePaid: Boolean = false,
    /** The address of the player's own server, as typed. */
    val address: String? = null,
)

/** Outbound port: the models a service currently offers. Models come and go, so this is asked, not hard-coded. */
fun interface AdvisorModelCatalog {
    suspend fun available(
        provider: AdvisorProvider,
        apiKey: String?,
        address: String?,
    ): Outcome<List<ListedModel>, AdvisorFailure>
}

/** Inbound port: the models of a provider that suit asking for a deck, best first. */
fun interface ListSuitableModels {
    suspend operator fun invoke(request: ModelRequest): Outcome<List<ListedModel>, AdvisorFailure>
}

/**
 * Models that take tools and a long prompt (the player's cards and the tournament data are about 22,000 tokens, and the
 * answer needs room). OpenRouter's are free ones unless the player asks for paid ones too; those that answer directly
 * come first, free before paid, then those with more room. A paid model needs a stated price: routers state none (OpenRouter
 * marks them -1) because they hand the request to any model. The lists of Anthropic, OpenAI and a server of the player's own are kept as they come (they state no tools or context, and the player knows what they run).
 */
class ListSuitableModelsService(
    private val catalog: AdvisorModelCatalog,
) : ListSuitableModels {
    override suspend fun invoke(request: ModelRequest): Outcome<List<ListedModel>, AdvisorFailure> =
        when (val outcome = catalog.available(request.provider, request.apiKey, request.address)) {
            is Outcome.Failure -> outcome
            is Outcome.Success -> Outcome.Success(arranged(outcome.value.filter { suits(it, request) }, request))
        }

    private fun arranged(
        models: List<ListedModel>,
        request: ModelRequest,
    ) = if (request.provider.listsEveryModel) models else models.sortedWith(ORDER)

    /** The `openrouter/free` router is left out: it hands the request to any free model, including ones that never answer. */
    private fun suits(
        model: ListedModel,
        request: ModelRequest,
    ) = request.provider.listsEveryModel ||
        (
            (model.free || (request.includePaid && model.hasPrice)) &&
                model.supportsTools && model.contextTokens >= MINIMUM_CONTEXT &&
                !model.id.startsWith(ROUTER_PREFIX)
        )

    private companion object {
        const val MINIMUM_CONTEXT = 100_000
        const val ROUTER_PREFIX = "openrouter/"
        val ORDER =
            compareBy<ListedModel> { !it.free }
                .thenBy { it.thinking }
                .thenByDescending { it.contextTokens }
                .thenBy { it.id }
    }
}
