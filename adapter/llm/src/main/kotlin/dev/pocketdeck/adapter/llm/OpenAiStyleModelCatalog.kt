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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Reads `GET /models` of a service with the OpenAI API: OpenAI itself (needs the key) or a server of the player's own such
 * as Ollama or LM Studio (the address is theirs, a key only if they set one). The list says nothing about tools or context,
 * so models are offered as they come; OpenAI's list also holds image, audio and embedding models, which are left out.
 */
class OpenAiStyleModelCatalog(
    private val client: HttpClient,
    private val openAiUrl: String = "https://api.openai.com/v1",
) : AdvisorModelCatalog {
    override suspend fun available(
        provider: AdvisorProvider,
        apiKey: String?,
        address: String?,
    ): Outcome<List<ListedModel>, AdvisorFailure> {
        val base =
            when (provider) {
                AdvisorProvider.OPENAI -> openAiUrl
                AdvisorProvider.OWN_SERVER -> address?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                else -> return Outcome.Success(emptyList())
            } ?: return Outcome.Failure(AdvisorFailure.Rejected("No server address is set"))
        return try {
            val response =
                client.get("$base/models") {
                    if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
                }
            read(response.status, response.bodyAsText(), provider)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            Outcome.Failure(AdvisorFailure.NotReachable(failure.message ?: failure::class.simpleName.orEmpty()))
        }
    }

    private fun read(
        status: HttpStatusCode,
        text: String,
        provider: AdvisorProvider,
    ): Outcome<List<ListedModel>, AdvisorFailure> {
        if (status == HttpStatusCode.Unauthorized) return Outcome.Failure(AdvisorFailure.InvalidKey)
        if (!status.isSuccess()) return Outcome.Failure(AdvisorFailure.Rejected(errorMessage(text)))
        val json = readJson(text) ?: return Outcome.Failure(AdvisorFailure.Unusable("The model list is not JSON"))
        val ids =
            json["data"]
                ?.jsonArray
                .orEmpty()
                .mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
        val chat = if (provider == AdvisorProvider.OPENAI) ids.filter(::isChatModel) else ids
        return Outcome.Success(chat.sorted().map(::listed))
    }

    private fun isChatModel(id: String) = CHAT_FAMILY.containsMatchIn(id) && !NOT_CHAT.containsMatchIn(id)

    private fun listed(id: String) =
        ListedModel(
            id = id,
            name = id,
            contextTokens = 0,
            free = false,
            supportsTools = true,
            thinking = Thinking.ANSWERS_DIRECTLY,
            expiresOn = null,
        )

    private companion object {
        val CHAT_FAMILY = Regex("^(gpt-|o\\d|chatgpt-)")
        val NOT_CHAT =
            Regex("audio|realtime|transcribe|tts|image|embedding|moderation|search|instruct|codex|whisper|dall-e")
    }
}
