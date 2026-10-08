package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorModelCatalog
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.ListedModel
import dev.pocketdeck.application.advice.Thinking
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Reads the Claude models a key may use from Anthropic's model list, newest first. The list needs the key, and the
 * request forces a tool call, so every model on it is usable; none of them thinks before answering that way.
 */
class AnthropicModelCatalog(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.anthropic.com",
) : AdvisorModelCatalog {
    override suspend fun available(
        provider: AdvisorProvider,
        apiKey: String?,
    ): Outcome<List<ListedModel>, AdvisorFailure> {
        if (provider != AdvisorProvider.ANTHROPIC) return Outcome.Success(emptyList())
        if (apiKey.isNullOrBlank()) return Outcome.Failure(AdvisorFailure.MissingKey)
        return try {
            val response =
                client.get("$baseUrl/v1/models") {
                    header("x-api-key", apiKey.trim())
                    header("anthropic-version", API_VERSION)
                    parameter("limit", PAGE_SIZE)
                }
            val status = response.status.value
            val text = response.bodyAsText()
            when {
                status == UNAUTHORIZED || status == FORBIDDEN -> Outcome.Failure(AdvisorFailure.InvalidKey)
                status == TOO_MANY -> Outcome.Failure(AdvisorFailure.Busy(errorMessage(text)))
                status !in OK_RANGE -> Outcome.Failure(AdvisorFailure.Rejected(errorMessage(text)))
                else -> modelsOf(text)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            Outcome.Failure(AdvisorFailure.NotReachable(failure.message ?: failure::class.simpleName.orEmpty()))
        }
    }

    private fun modelsOf(text: String): Outcome<List<ListedModel>, AdvisorFailure> {
        val json = readJson(text) ?: return Outcome.Failure(AdvisorFailure.Unusable("The model list is not JSON"))
        return Outcome.Success(json["data"]?.jsonArray.orEmpty().mapNotNull { toModel(it.jsonObject) })
    }

    private fun toModel(model: JsonObject): ListedModel? {
        val id = model["id"]?.jsonPrimitive?.contentOrNull ?: return null
        return ListedModel(
            id = id,
            name = model["display_name"]?.jsonPrimitive?.contentOrNull ?: id,
            contextTokens = model["max_input_tokens"]?.jsonPrimitive?.intOrNull ?: ASSUMED_CONTEXT,
            free = false,
            supportsTools = true,
            thinking = Thinking.ANSWERS_DIRECTLY,
            expiresOn = null,
        )
    }

    private companion object {
        const val API_VERSION = "2023-06-01"
        const val PAGE_SIZE = 100
        const val ASSUMED_CONTEXT = 200_000
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY = 429
        val OK_RANGE = 200..299
    }
}
