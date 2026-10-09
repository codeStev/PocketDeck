package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdviceFeedback
import dev.pocketdeck.application.advice.AdviceFormat
import dev.pocketdeck.application.advice.AdvicePromptWriter
import dev.pocketdeck.application.advice.AdviceRequest
import dev.pocketdeck.application.advice.AdvisorDiagnostics
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.DeckAdvice
import dev.pocketdeck.application.advice.ProviderAccess
import dev.pocketdeck.application.advice.ProviderDeckAdvisor
import dev.pocketdeck.application.advice.describe
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
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

/** The services that speak the OpenAI chat completions API, and where they differ from each other. */
enum class ChatDialect(
    /** The service's address; null when the player gives it (their own server). */
    val baseUrl: String?,
) {
    OPENROUTER("https://openrouter.ai/api/v1"),
    OPENAI("https://api.openai.com/v1"),
    OWN_SERVER(null),
}

/**
 * Asks a model through the OpenAI-style chat completions API: OpenRouter (the free models included), OpenAI, or a server of
 * the player's own such as Ollama or LM Studio. A model that supports tools hands the deck in as a function call; one that
 * does not may write it out as text, which is read too.
 */
class ChatCompletionsDeckAdvisor(
    private val client: HttpClient,
    private val dialect: ChatDialect,
    private val baseUrl: String? = dialect.baseUrl,
    private val diagnostics: AdvisorDiagnostics = AdvisorDiagnostics.NONE,
) : ProviderDeckAdvisor {
    override suspend fun advise(
        request: AdviceRequest,
        access: ProviderAccess,
    ): Outcome<DeckAdvice, AdvisorFailure> {
        val address =
            (baseUrl ?: access.address)?.trimEnd('/') ?: return Outcome.Failure(AdvisorFailure.Rejected(NO_ADDRESS))
        if (address.startsWith("http://", ignoreCase = true) && !access.plainHttpAllowed) {
            return Outcome.Failure(AdvisorFailure.Rejected(PLAIN_HTTP_OFF))
        }
        return when (
            val reply =
                client.postJson(
                    "$address/chat/completions",
                    headers(access),
                    body(request, access),
                    diagnostics,
                    ::classify,
                )
        ) {
            is Reply.Failed -> Outcome.Failure(reply.failure)
            is Reply.Answer -> read(reply.json)
        }
    }

    /** A server of the player's own often has no key; sending an empty bearer token would be rejected by some. */
    private fun headers(access: ProviderAccess): Map<String, String> =
        buildMap {
            if (access.apiKey.isNotBlank()) put("Authorization", "Bearer ${access.apiKey}")
            if (dialect == ChatDialect.OPENROUTER) put("X-Title", APP_NAME)
        }

    private fun body(
        request: AdviceRequest,
        access: ProviderAccess,
    ): JsonObject {
        val prompt = AdvicePromptWriter.write(request)
        return buildJsonObject {
            put("model", access.model)
            // OpenAI's newer models refuse max_tokens; the others know only that name.
            put(if (dialect == ChatDialect.OPENAI) "max_completion_tokens" else "max_tokens", MAX_TOKENS)
            putThinkingLimit(access.model)
            putJsonArray("tools") { add(tool()) }
            // Local runtimes differ in what they accept here; without it they still answer, and a text answer is read too.
            if (dialect != ChatDialect.OWN_SERVER) {
                putJsonObject("tool_choice") {
                    put("type", "function")
                    putJsonObject("function") { put("name", AdviceFormat.TOOL_NAME) }
                }
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

    /** A thinking model spends most of its room on thinking; this keeps that short so the deck still fits. */
    private fun JsonObjectBuilder.putThinkingLimit(model: String) {
        when {
            dialect == ChatDialect.OPENROUTER -> putJsonObject("reasoning") { put("effort", "low") }
            dialect == ChatDialect.OPENAI && REASONING_MODEL.containsMatchIn(model) -> put("reasoning_effort", "low")
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
        val choice = answer["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message =
            choice?.get("message")?.jsonObject
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
        val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        describe(answer, message, arguments, content, finishReason)
        if (arguments == null && finishReason == "length") {
            val model = answer["model"]?.jsonPrimitive?.contentOrNull ?: "The model"
            return Outcome.Failure(
                AdvisorFailure.Unusable("$model ran out of room while thinking; choose another model"),
            )
        }
        if (arguments == null && content.isBlank()) {
            return Outcome.Failure(
                AdvisorFailure.Unusable("The model sent no deck (finish reason: ${finishReason ?: "unknown"})"),
            )
        }
        return DeckTool.parseText(arguments ?: content)
    }

    /** What kind of answer came back and from which model, so a model that does not deliver can be recognised. */
    private fun describe(
        answer: JsonObject,
        message: JsonObject,
        arguments: String?,
        content: String,
        finishReason: String?,
    ) {
        val reasoning = message["reasoning"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val shown = (arguments ?: content).replace(Regex("\\s+"), " ").take(PREVIEW_LENGTH)
        val model = answer["model"]?.jsonPrimitive?.contentOrNull ?: "an unknown model"
        val call = if (arguments != null) "function call" else "no function call"
        diagnostics.note(
            "Answer from $model: finish reason ${finishReason ?: "unknown"}, $call, ${content.length} characters of " +
                "text, ${reasoning.length} characters of reasoning; starts with: $shown",
        )
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
        const val NO_ADDRESS = "No server address is set"
        const val PLAIN_HTTP_OFF = "The address starts with http://; switch on plain http in Settings or use https"
        val REASONING_MODEL = Regex("^(gpt-5|o\\d)")
        const val PREVIEW_LENGTH = 400
        const val MAX_TOKENS = 12_000
        const val UNAUTHORIZED = 401
        const val REQUEST_TIMEOUT = 408
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
    }
}
