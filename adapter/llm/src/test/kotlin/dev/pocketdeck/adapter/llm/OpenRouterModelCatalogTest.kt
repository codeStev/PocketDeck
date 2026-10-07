package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.Thinking
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

class OpenRouterModelCatalogTest {
    private val answer =
        """{"data":[
        {"id":"google/gemma-4-31b-it:free","name":"Gemma 4 31B (free)","context_length":262144,
         "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools","structured_outputs","reasoning"],
         "reasoning":{"mandatory":false,"default_enabled":false},"expiration_date":null},
        {"id":"nvidia/nemotron-3-super-120b-a12b:free","name":"Nemotron 3 Super (free)","context_length":262144,
         "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools","reasoning"],
         "reasoning":{"mandatory":false,"default_enabled":true},"expiration_date":"2026-12-31"},
        {"id":"liquid/lfm-2.5-2.6b:free","name":"LFM","context_length":65536,
         "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools"],"reasoning":{"mandatory":true}},
        {"id":"cohere/silent-flag:free","name":"Silent flag","context_length":256000,
         "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools","reasoning"],"reasoning":{"mandatory":false}},
        {"id":"x/no-reasoning:free","name":"No reasoning","context_length":128000,
         "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools"],"reasoning":null},
        {"id":"anthropic/claude-x","name":"Claude X","context_length":200000,
         "pricing":{"prompt":"0.000003","completion":"0.000015"},"supported_parameters":["tools"],"reasoning":null},
        {"id":"x/no-pricing","name":"No pricing","context_length":1000,"supported_parameters":[]}
        ]}"""

    private fun client(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) })

    @Test
    fun `reads the models with their price, context, tools and thinking`() =
        runTest {
            val models =
                (
                    OpenRouterModelCatalog(
                        client(answer),
                        "https://or.test/api/v1",
                    ).available(AdvisorProvider.OPENROUTER) as Outcome.Success
                ).value

            val gemma = models.first { it.id == "google/gemma-4-31b-it:free" }
            gemma.free shouldBe true
            gemma.supportsTools shouldBe true
            gemma.contextTokens shouldBe 262_144
            gemma.thinking shouldBe Thinking.ANSWERS_DIRECTLY
            gemma.name shouldBe "Gemma 4 31B (free)"
            models.first { it.id.startsWith("nvidia") }.thinking shouldBe Thinking.THINKS_BY_DEFAULT
            models.first { it.id.startsWith("nvidia") }.expiresOn shouldBe "2026-12-31"
            models.first { it.id.startsWith("liquid") }.thinking shouldBe Thinking.MUST_THINK
        }

    @Test
    fun `a model that costs anything or has no price is not free`() =
        runTest {
            val models =
                (
                    OpenRouterModelCatalog(
                        client(answer),
                        "https://or.test",
                    ).available(AdvisorProvider.OPENROUTER) as Outcome.Success
                ).value

            models.first { it.id == "anthropic/claude-x" }.free shouldBe false
            models.first { it.id == "x/no-pricing" }.free shouldBe false
        }

    @Test
    fun `asks the public model list`() =
        runTest {
            var asked = ""
            val http =
                HttpClient(
                    MockEngine { request ->
                        asked = request.url.toString()
                        respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )

            OpenRouterModelCatalog(http, "https://or.test/api/v1").available(AdvisorProvider.OPENROUTER)

            asked shouldBe "https://or.test/api/v1/models"
        }

    @Test
    fun `lists nothing for a provider it does not know`() =
        runTest {
            OpenRouterModelCatalog(client(answer), "https://or.test").available(AdvisorProvider.ANTHROPIC) shouldBe
                Outcome.Success(emptyList())
        }

    @Test
    fun `reports a missing connection and an unreadable answer`() =
        runTest {
            val offline = HttpClient(MockEngine { throw IOException("no network") })

            OpenRouterModelCatalog(offline, "https://x").available(AdvisorProvider.OPENROUTER) shouldBe
                Outcome.Failure(AdvisorFailure.NotReachable("no network"))
            (
                OpenRouterModelCatalog(
                    client("<html>"),
                    "https://x",
                ).available(AdvisorProvider.OPENROUTER) as Outcome.Failure
            ).reason.javaClass shouldBe AdvisorFailure.Unusable::class.java
        }

    @Test
    fun `a model whose thinking default is not stated counts as one that thinks first`() =
        runTest {
            val models =
                (
                    OpenRouterModelCatalog(
                        client(answer),
                        "https://or.test",
                    ).available(AdvisorProvider.OPENROUTER) as Outcome.Success
                ).value

            // Seen in practice: models with this metadata thought for minutes and never answered.
            models.first { it.id == "cohere/silent-flag:free" }.thinking shouldBe Thinking.THINKS_BY_DEFAULT
            models.first { it.id == "x/no-reasoning:free" }.thinking shouldBe Thinking.ANSWERS_DIRECTLY
        }
}
