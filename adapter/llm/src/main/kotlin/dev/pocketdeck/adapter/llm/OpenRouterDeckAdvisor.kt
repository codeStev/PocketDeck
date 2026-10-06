package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdviceFeedback
import dev.pocketdeck.application.advice.AdviceFormat
import dev.pocketdeck.application.advice.AdvicePromptWriter
import dev.pocketdeck.application.advice.AdviceRequest
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.DeckAdvice
import dev.pocketdeck.application.advice.ProviderAccess
import dev.pocketdeck.application.advice.ProviderDeckAdvisor
import dev.pocketdeck.application.advice.describe
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Asks any model on OpenRouter through its OpenAI-style chat completions API; the free ones included. A model that
 * supports tools hands the deck in as a function call; one that does not may write it out as text, which is read too.
 */
class OpenRouterDeckAdvisor(
    private val client: HttpClient,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
) : ProviderDeckAdvisor {
    override suspend fun advise(
        request: AdviceRequest,
        access: ProviderAccess,
    ): Outcome<DeckAdvice, AdvisorFailure> =
        when (
            val reply =
                client.postJson(
                    "$baseUrl/chat/completions",
                    headers(access),
                    body(request, access),
                    ::classify,
                )
        ) {
            is Reply.Failed -> Outcome.Failure(reply.failure)
            is Reply.Answer -> read(reply.json)
        }

    private fun headers(access: ProviderAccess) =
        mapOf("Authorization" to "Bearer ${access.apiKey}", "X-Title" to APP_NAME)

    private fun body(
        request: AdviceRequest,
        access: ProviderAccess,
    ): JsonObject {
        val prompt = AdvicePromptWriter.write(request)
        return buildJsonObject {
            put("model", access.model)
            put("max_tokens", MAX_TOKENS)
            putJsonArray("tools") { add(tool()) }
            putJsonObject("tool_choice") {
                put("type", "function")
                putJsonObject("function") { put("name", AdviceFormat.TOOL_NAME) }
            }
            put(
                "messages",
                messages(
                    prompt.system,
                    "${prompt.collection}\n\n${prompt.tournament}\n\n${prompt.task}",
                    request.feedback,
                ),
            )
        }
    }

    private fun tool() =
        buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", AdviceFormat.TOOL_NAME)
                put("description", AdviceFormat.TOOL_DESCRIPTION)
                put("parameters", DeckTool.schema)
            }
        }

    private fun messages(
        system: String,
        user: String,
        feedback: List<AdviceFeedback>,
    ): JsonArray =
        buildJsonArray {
            add(message("system", system))
            add(message("user", user))
            feedback.forEachIndexed { index, entry ->
                val id = "call_correction_$index"
                add(assistantCall(id, entry))
                add(
                    buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", id)
                        put("content", entry.describe())
                    },
                )
            }
        }

    private fun message(
        role: String,
        content: String,
    ) = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    private fun assistantCall(
        id: String,
        entry: AdviceFeedback,
    ) = buildJsonObject {
        put("role", "assistant")
        putJsonArray("tool_calls") {
            add(
                buildJsonObject {
                    put("id", id)
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", AdviceFormat.TOOL_NAME)
                        put("arguments", DeckTool.inputOf(entry.advice).toString())
                    }
                },
            )
        }
    }

    private fun read(answer: JsonObject): Outcome<DeckAdvice, AdvisorFailure> {
        answer["error"]?.let { return Outcome.Failure(classify(code(it), errorMessage(answer.toString()))) }
        val message =
            answer["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?: return Outcome.Failure(AdvisorFailure.Unusable("The model sent no answer"))
        val arguments =
            message["tool_calls"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("function")
                ?.jsonObject
                ?.get("arguments")
                ?.jsonPrimitive
                ?.contentOrNull
        val text = arguments ?: message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return DeckTool.parseText(text)
    }

    /** OpenRouter sometimes reports an upstream failure inside a successful answer, with the status as `code`. */
    private fun code(error: kotlinx.serialization.json.JsonElement) =
        (error as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull ?: 0

    private fun classify(
        status: Int,
        message: String,
    ): AdvisorFailure =
        when {
            status == UNAUTHORIZED -> {
                AdvisorFailure.InvalidKey
            }

            status == REQUEST_TIMEOUT || status == TOO_MANY_REQUESTS || status >= SERVER_ERROR -> {
                AdvisorFailure.Busy(
                    message,
                )
            }

            else -> {
                AdvisorFailure.Rejected(message)
            }
        }

    private companion object {
        const val APP_NAME = "PocketDeck"
        const val MAX_TOKENS = 4096
        const val UNAUTHORIZED = 401
        const val REQUEST_TIMEOUT = 408
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
    }
}
