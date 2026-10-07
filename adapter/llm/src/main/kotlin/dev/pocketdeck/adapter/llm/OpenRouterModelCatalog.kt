package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorModelCatalog
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.ListedModel
import dev.pocketdeck.application.advice.Thinking
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** Reads OpenRouter's public model list (it needs no key), which says what each model costs and can do. */
class OpenRouterModelCatalog(
    private val client: HttpClient,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
) : AdvisorModelCatalog {
    override suspend fun available(provider: AdvisorProvider): Outcome<List<ListedModel>, AdvisorFailure> {
        if (provider != AdvisorProvider.OPENROUTER) return Outcome.Success(emptyList())
        return try {
            val response = client.get("$baseUrl/models")
            val json = readJson(response.bodyAsText())
            when {
                !response.status.isSuccess() -> {
                    Outcome.Failure(
                        AdvisorFailure.Rejected("OpenRouter answered ${response.status}"),
                    )
                }

                json == null -> {
                    Outcome.Failure(AdvisorFailure.Unusable("The model list is not JSON"))
                }

                else -> {
                    Outcome.Success(json["data"]?.jsonArray.orEmpty().mapNotNull { toModel(it.jsonObject) })
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            Outcome.Failure(AdvisorFailure.NotReachable(failure.message ?: failure::class.simpleName.orEmpty()))
        }
    }

    private fun toModel(model: JsonObject): ListedModel? {
        val id = model["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val pricing = model["pricing"] as? JsonObject
        val parameters = model["supported_parameters"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        return ListedModel(
            id = id,
            name = model["name"]?.jsonPrimitive?.contentOrNull ?: id,
            contextTokens = model["context_length"]?.jsonPrimitive?.intOrNull ?: 0,
            free = isFree(pricing),
            supportsTools = "tools" in parameters,
            thinking = thinkingOf(model["reasoning"] as? JsonObject),
            expiresOn = model["expiration_date"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Free means both prices are stated and zero; a model without prices is not assumed to be free. */
    private fun isFree(pricing: JsonObject?): Boolean {
        val prompt = pricing?.get("prompt")?.jsonPrimitive?.doubleOrNull
        val completion = pricing?.get("completion")?.jsonPrimitive?.doubleOrNull
        return prompt == 0.0 && completion == 0.0
    }

    /**
     * Only a model that does not do reasoning at all, or states that it is off by default, answers directly. One that
     * says nothing about its default is treated as thinking first: that is what such models did when tried.
     */
    private fun thinkingOf(reasoning: JsonObject?): Thinking {
        if (reasoning == null) return Thinking.ANSWERS_DIRECTLY
        if (reasoning["mandatory"]?.jsonPrimitive?.booleanOrNull == true) return Thinking.MUST_THINK
        val default = reasoning["default_enabled"]?.jsonPrimitive?.booleanOrNull
        return if (default == false) Thinking.ANSWERS_DIRECTLY else Thinking.THINKS_BY_DEFAULT
    }
}
