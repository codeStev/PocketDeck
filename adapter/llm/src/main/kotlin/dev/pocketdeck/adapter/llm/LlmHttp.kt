package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.advice.AdvisorDiagnostics
import dev.pocketdeck.application.advice.AdvisorFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.time.TimeSource

/** What came back from a request: the answer's JSON, or why there is none. */
internal sealed interface Reply {
    data class Answer(
        val json: JsonObject,
    ) : Reply

    data class Failed(
        val failure: AdvisorFailure,
    ) : Reply
}

private const val MESSAGE_PREVIEW = 200
private val lenient = Json { ignoreUnknownKeys = true }

/**
 * Posts [body] as JSON and reads the answer. [classify] decides what a failing status means for the player, since the
 * services differ (402 is "out of credits" at one and unused at the other).
 */
internal suspend fun HttpClient.postJson(
    url: String,
    headers: Map<String, String>,
    body: JsonObject,
    diagnostics: AdvisorDiagnostics,
    classify: (status: Int, message: String) -> AdvisorFailure,
): Reply =
    try {
        diagnostics.note("POST $url (${body.toString().length} characters)")
        val started = TimeSource.Monotonic.markNow()
        val response =
            post(url) {
                headers.forEach { (name, value) -> header(name, value) }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        val text = response.bodyAsText()
        val status = response.status.value
        diagnostics.note("HTTP $status, ${text.length} characters, ${started.elapsedNow().inWholeMilliseconds} ms")
        if (status in 200..299) {
            readJson(text)?.let { Reply.Answer(it) } ?: Reply.Failed(AdvisorFailure.Unusable("The answer is not JSON"))
        } else {
            Reply.Failed(classify(status, errorMessage(text)))
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: IOException) {
        diagnostics.note("Request failed: ${failure::class.simpleName}: ${failure.message}")
        Reply.Failed(AdvisorFailure.NotReachable(failure.message ?: failure::class.simpleName.orEmpty()))
    }

internal fun readJson(text: String): JsonObject? =
    try {
        lenient.parseToJsonElement(text).jsonObject
    } catch (failure: SerializationException) {
        null
    } catch (failure: IllegalArgumentException) {
        null
    }

/** The message the service gives for an error, from `{"error":{"message":…}}`, or the start of whatever it sent. */
internal fun errorMessage(text: String): String {
    val error = readJson(text)?.get("error")
    val message =
        when (error) {
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    return message ?: text.take(MESSAGE_PREVIEW)
}
