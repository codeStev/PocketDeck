package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException

class OpenAiStyleModelCatalogTest {
    private var seen: HttpRequestData? = null

    private fun client(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = HttpClient(
        MockEngine {
            seen = it
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private val openAiList =
        """{"object":"list","data":[{"id":"gpt-5-mini"},{"id":"text-embedding-3-small"},{"id":"gpt-4o-audio-preview"},
        {"id":"o3"},{"id":"dall-e-3"},{"id":"gpt-image-1"},{"id":"whisper-1"},{"id":"gpt-4o"}]}"""

    @Test
    fun `lists the chat models of openai, sorted, without image, audio and embedding models`() =
        runTest {
            val outcome =
                OpenAiStyleModelCatalog(client(openAiList), "https://oa.test/v1")
                    .available(AdvisorProvider.OPENAI, " sk-1 ", null)

            (outcome as Outcome.Success).value.map { it.id } shouldBe listOf("gpt-4o", "gpt-5-mini", "o3")
            seen!!.url.toString() shouldBe "https://oa.test/v1/models"
            seen!!.headers["Authorization"] shouldBe "Bearer sk-1"
        }

    @Test
    fun `lists every model of a server of the player's own, at their address and without a key`() =
        runTest {
            val outcome =
                OpenAiStyleModelCatalog(client("""{"data":[{"id":"qwen3:8b"},{"id":"llama3.1"}]}"""))
                    .available(AdvisorProvider.OWN_SERVER, null, "http://pc:11434/v1/")

            (outcome as Outcome.Success).value.map { it.id } shouldBe listOf("llama3.1", "qwen3:8b")
            seen!!.url.toString() shouldBe "http://pc:11434/v1/models"
            seen!!.headers.contains("Authorization") shouldBe false
        }

    @Test
    fun `asks for an address when the player has not given one`() =
        runTest {
            val outcome = OpenAiStyleModelCatalog(client("{}")).available(AdvisorProvider.OWN_SERVER, null, " ")

            (outcome as Outcome.Failure).reason.javaClass shouldBe AdvisorFailure.Rejected::class.java
        }

    @Test
    fun `reports a refused key, a missing connection and an answer that is not json`() =
        runTest {
            val refused =
                OpenAiStyleModelCatalog(client("{}", HttpStatusCode.Unauthorized))
                    .available(AdvisorProvider.OPENAI, "k", null)
            val offline =
                OpenAiStyleModelCatalog(HttpClient(MockEngine { throw IOException("no route") }))
                    .available(AdvisorProvider.OWN_SERVER, null, "http://pc/v1")
            val garbled = OpenAiStyleModelCatalog(client("<html>")).available(AdvisorProvider.OPENAI, "k", null)

            refused shouldBe Outcome.Failure(AdvisorFailure.InvalidKey)
            offline shouldBe Outcome.Failure(AdvisorFailure.NotReachable("no route"))
            (garbled as Outcome.Failure).reason.javaClass shouldBe AdvisorFailure.Unusable::class.java
        }

    @Test
    fun `leaves other providers to their own catalogs`() =
        runTest {
            OpenAiStyleModelCatalog(client("{}")).available(AdvisorProvider.ANTHROPIC, "k", null) shouldBe
                Outcome.Success(emptyList())
        }
}
