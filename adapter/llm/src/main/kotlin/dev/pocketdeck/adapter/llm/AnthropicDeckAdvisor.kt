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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Asks Claude through Anthropic's Messages API. The deck comes back as the input of a forced tool call. The long list
 * of the player's cards is marked for prompt caching, so the corrections that follow a wrong answer reuse it.
 */
class AnthropicDeckAdvisor(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.anthropic.com",
) : ProviderDeckAdvisor {
    override suspend fun advise(
        request: AdviceRequest,
        access: ProviderAccess,
    ): Outcome<DeckAdvice, AdvisorFailure> =
        when (val reply = client.postJson("$baseUrl/v1/messages", headers(access), body(request, access), ::classify)) {
            is Reply.Failed -> Outcome.Failure(reply.failure)
            is Reply.Answer -> toolInput(reply.json)?.let(DeckTool::parse) ?: unusable(reply.json)
        }

    private fun headers(access: ProviderAccess) =
        mapOf("x-api-key" to access.apiKey, "anthropic-version" to API_VERSION)

    private fun body(
        request: AdviceRequest,
        access: ProviderAccess,
    ): JsonObject {
        val prompt = AdvicePromptWriter.write(request)
        return buildJsonObject {
            put("model", access.model)
            put("max_tokens", MAX_TOKENS)
            put("system", prompt.system)
            putJsonArray("tools") { add(tool()) }
            putJsonObject("tool_choice") {
                put("type", "tool")
                put("name", AdviceFormat.TOOL_NAME)
            }
            put("messages", messages(prompt.collection, prompt.tournament, prompt.task, request.feedback))
        }
    }

    private fun tool() =
        buildJsonObject {
            put("name", AdviceFormat.TOOL_NAME)
            put("description", AdviceFormat.TOOL_DESCRIPTION)
            put("input_schema", DeckTool.schema)
        }

    private fun messages(
        collection: String,
        tournament: String,
        task: String,
        feedback: List<AdviceFeedback>,
    ): JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(text(collection, cached = true))
                        add(text(tournament))
                        add(text(task))
                    }
                },
            )
            feedback.forEachIndexed { index, entry ->
                val id = "toolu_correction_$index"
                add(assistantCall(id, entry))
                add(correction(id, entry))
            }
        }

    private fun text(
        content: String,
        cached: Boolean = false,
    ) = buildJsonObject {
        put("type", "text")
        put("text", content)
        if (cached) putJsonObject("cache_control") { put("type", "ephemeral") }
    }

    private fun assistantCall(
        id: String,
        entry: AdviceFeedback,
    ) = buildJsonObject {
        put("role", "assistant")
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "tool_use")
                    put("id", id)
                    put("name", AdviceFormat.TOOL_NAME)
                    put("input", DeckTool.inputOf(entry.advice))
                },
            )
        }
    }

    private fun correction(
        id: String,
        entry: AdviceFeedback,
    ) = buildJsonObject {
        put("role", "user")
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", id)
                    put("content", entry.describe())
                    put("is_error", true)
                },
            )
        }
    }

    private fun toolInput(answer: JsonObject): JsonObject? =
        answer["content"]
            ?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull {
                it["type"]?.jsonPrimitive?.content == "tool_use" &&
                    it["name"]?.jsonPrimitive?.content == AdviceFormat.TOOL_NAME
            }?.get("input")
            ?.jsonObject

    private fun unusable(answer: JsonObject): Outcome<DeckAdvice, AdvisorFailure> =
        Outcome.Failure(
            AdvisorFailure.Unusable(
                "Claude answered without a deck (${answer["stop_reason"]?.jsonPrimitive?.content ?: "no reason"})",
            ),
        )

    private fun classify(
        status: Int,
        message: String,
    ): AdvisorFailure =
        when {
            status == UNAUTHORIZED || status == FORBIDDEN -> AdvisorFailure.InvalidKey
            status == TOO_MANY_REQUESTS || status >= SERVER_ERROR -> AdvisorFailure.Busy(message)
            else -> AdvisorFailure.Rejected(message)
        }

    private companion object {
        const val API_VERSION = "2023-06-01"
        const val MAX_TOKENS = 4096
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
    }
}
