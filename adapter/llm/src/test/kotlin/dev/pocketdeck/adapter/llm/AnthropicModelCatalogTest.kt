package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException

class AnthropicModelCatalogTest {
    private val answer =
        """{"data":[
        {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-05-01T00:00:00Z",
         "max_input_tokens":500000},
        {"type":"model","id":"claude-haiku-4-5-20251001","display_name":"Claude Haiku 4.5"}
        ],"has_more":false}"""

    private fun client(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ) = HttpClient(
        MockEngine { request ->
            onRequest(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    @Test
    fun `reads the Claude models a key can use, with their names`() =
        runTest {
            val models =
                (
                    AnthropicModelCatalog(client(answer), "https://a.test")
                        .available(AdvisorProvider.ANTHROPIC, "sk-ant", null) as Outcome.Success
                ).value

            models.map { it.id } shouldBe listOf("claude-opus-5-5", "claude-haiku-4-5-20251001")
            models.first().name shouldBe "Claude Opus 5.5"
            models.first().contextTokens shouldBe 500_000
            models.last().contextTokens shouldBe 200_000
            models.all { it.supportsTools && !it.free } shouldBe true
        }

    @Test
    fun `sends the key and the api version`() =
        runTest {
            var key: String? = null
            var version: String? = null
            AnthropicModelCatalog(
                client(answer) {
                    key = it.headers["x-api-key"]
                    version = it.headers["anthropic-version"]
                },
                "https://a.test",
            ).available(AdvisorProvider.ANTHROPIC, " sk-ant ", null)

            key shouldBe "sk-ant"
            version shouldBe "2023-06-01"
        }

    @Test
    fun `needs a key and says so without asking`() =
        runTest {
            var asked = false
            val catalog = AnthropicModelCatalog(client(answer) { asked = true }, "https://a.test")

            catalog.available(AdvisorProvider.ANTHROPIC, "  ", null) shouldBe Outcome.Failure(AdvisorFailure.MissingKey)
            asked shouldBe false
        }

    @Test
    fun `tells a rejected key, a busy service and a missing connection apart`() =
        runTest {
            val unauthorized = client("""{"error":{"message":"invalid x-api-key"}}""", HttpStatusCode.Unauthorized)
            AnthropicModelCatalog(
                unauthorized,
                "https://a.test",
            ).available(AdvisorProvider.ANTHROPIC, "k", null) shouldBe
                Outcome.Failure(AdvisorFailure.InvalidKey)

            val busy = client("""{"error":{"message":"slow down"}}""", HttpStatusCode.TooManyRequests)
            AnthropicModelCatalog(busy, "https://a.test").available(AdvisorProvider.ANTHROPIC, "k", null) shouldBe
                Outcome.Failure(AdvisorFailure.Busy("slow down"))

            val offline = HttpClient(MockEngine { throw IOException("no network") })
            AnthropicModelCatalog(offline, "https://a.test").available(AdvisorProvider.ANTHROPIC, "k", null) shouldBe
                Outcome.Failure(AdvisorFailure.NotReachable("no network"))
        }

    @Test
    fun `lists nothing for another provider`() =
        runTest {
            AnthropicModelCatalog(
                client(answer),
                "https://a.test",
            ).available(AdvisorProvider.OPENROUTER, "k", null) shouldBe
                Outcome.Success(emptyList())
        }
}
