package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdviceFeedback
import dev.pocketdeck.application.advice.AdviceRequest
import dev.pocketdeck.application.advice.AdvisedCard
import dev.pocketdeck.application.advice.AdvisorDiagnostics
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.DeckAdvice
import dev.pocketdeck.application.advice.ProviderAccess
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.meta.MetaSnapshot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.IOException

class ProviderAdvisorsTest {
    private val request =
        AdviceRequest(
            wish = "a fire deck",
            catalog = CardCatalog.EMPTY,
            inventory = Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
            meta = MetaSnapshot.EMPTY,
        )
    private val access = ProviderAccess("secret-key", "some/model")
    private val deckJson =
        """{"title":"Fire Rush","rationale":"Fast attackers.","cards":[{"id":"a1-033","name":"Arcanine ex","count":2},{"id":"a1-219","count":"1"}],"energy":["fire","Lightning"]}"""
    private val expected =
        DeckAdvice(
            "Fire Rush",
            "Fast attackers.",
            listOf(AdvisedCard("a1-033", "Arcanine ex", 2), AdvisedCard("a1-219", null, 1)),
            listOf(EnergyType.FIRE, EnergyType.LIGHTNING),
        )

    private class Captured {
        var request: HttpRequestData? = null
        var body: JsonObject = JsonObject(emptyMap())
    }

    private fun client(
        captured: Captured,
        answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(
        MockEngine { req ->
            captured.request = req
            captured.body = Json.parseToJsonElement(String(req.body.toByteArray())).jsonObject
            answer(req)
        },
    )

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    // ---- Anthropic

    private fun anthropicAnswer(input: String = deckJson) =
        """{"id":"msg_1","type":"message","role":"assistant","stop_reason":"tool_use","content":[{"type":"text","text":"ok"},{"type":"tool_use","id":"toolu_1","name":"submit_deck","input":$input}]}"""

    @Test
    fun `anthropic sends the key, the model and forces the deck tool`() =
        runTest {
            val captured = Captured()
            val advisor = AnthropicDeckAdvisor(client(captured) { json(anthropicAnswer()) }, "https://api.test")

            advisor.advise(request, access)

            captured.request!!.headers["x-api-key"] shouldBe "secret-key"
            captured.request!!.headers["anthropic-version"] shouldBe "2023-06-01"
            captured.request!!.url.toString() shouldBe "https://api.test/v1/messages"
            captured.body["model"]!!.jsonPrimitive.content shouldBe "some/model"
            captured.body["tool_choice"]!!
                .jsonObject["name"]!!
                .jsonPrimitive.content shouldBe "submit_deck"
            captured.body["tools"]!!
                .jsonArray
                .single()
                .jsonObject["name"]!!
                .jsonPrimitive.content shouldBe
                "submit_deck"
        }

    @Test
    fun `anthropic marks the long collection for caching and passes the wish on`() =
        runTest {
            val captured = Captured()
            AnthropicDeckAdvisor(
                client(captured) { json(anthropicAnswer()) },
                "https://api.test",
            ).advise(request, access)

            val blocks =
                captured.body["messages"]!!
                    .jsonArray
                    .first()
                    .jsonObject["content"]!!
                    .jsonArray
            blocks
                .first()
                .jsonObject["cache_control"]!!
                .jsonObject["type"]!!
                .jsonPrimitive.content shouldBe "ephemeral"
            blocks
                .last()
                .jsonObject["text"]!!
                .jsonPrimitive.content shouldContain "a fire deck"
        }

    @Test
    fun `anthropic reads the deck from the tool call`() =
        runTest {
            val outcome =
                AnthropicDeckAdvisor(
                    client(Captured()) {
                        json(anthropicAnswer())
                    },
                    "https://api.test",
                ).advise(request, access)

            outcome shouldBe Outcome.Success(expected)
        }

    @Test
    fun `anthropic tells the model what was wrong with its earlier deck`() =
        runTest {
            val captured = Captured()
            val feedback = AdviceFeedback(expected, listOf("The deck has 3 cards but must have exactly 20"))

            AnthropicDeckAdvisor(client(captured) { json(anthropicAnswer()) }, "https://api.test")
                .advise(request.copy(feedback = listOf(feedback)), access)

            val messages = captured.body["messages"]!!.jsonArray
            messages.map { it.jsonObject["role"]!!.jsonPrimitive.content } shouldBe listOf("user", "assistant", "user")
            val result =
                messages
                    .last()
                    .jsonObject["content"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            result["type"]!!.jsonPrimitive.content shouldBe "tool_result"
            result["is_error"]!!.jsonPrimitive.content shouldBe "true"
            result["content"]!!.jsonPrimitive.content shouldContain "exactly 20"
        }

    @Test
    fun `anthropic maps error answers`() =
        runTest {
            val invalid =
                AnthropicDeckAdvisor(
                    client(Captured()) {
                        json("""{"error":{"message":"bad"}}""", HttpStatusCode.Unauthorized)
                    },
                    "https://x",
                ).advise(request, access)
            val busy =
                AnthropicDeckAdvisor(
                    client(Captured()) {
                        json("""{"error":{"message":"slow down"}}""", HttpStatusCode.TooManyRequests)
                    },
                    "https://x",
                ).advise(request, access)
            val overloaded =
                AnthropicDeckAdvisor(
                    client(Captured()) {
                        json("""{"error":{"message":"busy"}}""", HttpStatusCode(529, "Overloaded"))
                    },
                    "https://x",
                ).advise(request, access)
            val rejected =
                AnthropicDeckAdvisor(
                    client(Captured()) {
                        json("""{"error":{"message":"model not found"}}""", HttpStatusCode.NotFound)
                    },
                    "https://x",
                ).advise(request, access)

            invalid shouldBe Outcome.Failure(AdvisorFailure.InvalidKey)
            busy shouldBe Outcome.Failure(AdvisorFailure.Busy("slow down"))
            overloaded shouldBe Outcome.Failure(AdvisorFailure.Busy("busy"))
            rejected shouldBe Outcome.Failure(AdvisorFailure.Rejected("model not found"))
        }

    @Test
    fun `anthropic reports a missing connection and an answer without a deck`() =
        runTest {
            val offline =
                AnthropicDeckAdvisor(
                    client(Captured()) {
                        throw IOException("no network")
                    },
                    "https://x",
                ).advise(request, access)
            val noDeck =
                AnthropicDeckAdvisor(
                    client(Captured()) { json("""{"content":[{"type":"text","text":"I cannot"}]}""") },
                    "https://x",
                ).advise(request, access)

            offline shouldBe Outcome.Failure(AdvisorFailure.NotReachable("no network"))
            (noDeck as Outcome.Failure).reason.javaClass shouldBe AdvisorFailure.Unusable::class.java
        }

    // ---- OpenRouter

    private fun openRouterAnswer(arguments: String = deckJson) =
        """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"submit_deck","arguments":${JsonPrimitive(
            arguments,
        )}}}]}}]}"""

    @Test
    fun `openrouter sends the bearer key, the model and the deck function`() =
        runTest {
            val captured = Captured()

            openRouterAdvisor(
                client(captured) { json(openRouterAnswer()) },
                "https://or.test/api/v1",
            ).advise(request, access)

            captured.request!!.headers["Authorization"] shouldBe "Bearer secret-key"
            captured.request!!.url.toString() shouldBe "https://or.test/api/v1/chat/completions"
            captured.body["model"]!!.jsonPrimitive.content shouldBe "some/model"
            val function =
                captured.body["tools"]!!
                    .jsonArray
                    .single()
                    .jsonObject["function"]!!
                    .jsonObject
            function["name"]!!.jsonPrimitive.content shouldBe "submit_deck"
            captured.body["tool_choice"]!!
                .jsonObject["function"]!!
                .jsonObject["name"]!!
                .jsonPrimitive.content shouldBe
                "submit_deck"
        }

    @Test
    fun `openrouter leaves a thinking model room to think and asks it to think briefly`() =
        runTest {
            val captured = Captured()

            openRouterAdvisor(
                client(captured) { json(openRouterAnswer()) },
                "https://or.test/api/v1",
            ).advise(request, access)

            (
                captured.body["max_tokens"]!!
                    .jsonPrimitive.content
                    .toInt() >= 8000
            ) shouldBe true
            captured.body["reasoning"]!!
                .jsonObject["effort"]!!
                .jsonPrimitive.content shouldBe "low"
        }

    @Test
    fun `openrouter says so when the model ran out of room while thinking`() =
        runTest {
            val body =
                """{"model":"x/y:free","choices":[{"finish_reason":"length",""" +
                    """"message":{"role":"assistant","content":null,"reasoning":"Let me think..."}}]}"""

            val outcome =
                openRouterAdvisor(
                    client(Captured()) { json(body) },
                    "https://or.test",
                ).advise(request, access)

            val reason = ((outcome as Outcome.Failure).reason as AdvisorFailure.Unusable).reason
            reason shouldContain "ran out of room"
            reason shouldContain "x/y:free"
        }

    @Test
    fun `openrouter puts the rules in the system message and the cards in the user message`() =
        runTest {
            val captured = Captured()
            openRouterAdvisor(
                client(captured) { json(openRouterAnswer()) },
                "https://or.test/api/v1",
            ).advise(request, access)

            val messages = captured.body["messages"]!!.jsonArray
            messages.map { it.jsonObject["role"]!!.jsonPrimitive.content } shouldBe listOf("system", "user")
            messages
                .first()
                .jsonObject["content"]!!
                .jsonPrimitive.content shouldContain "exactly 20"
            messages
                .last()
                .jsonObject["content"]!!
                .jsonPrimitive.content shouldContain "a fire deck"
        }

    @Test
    fun `openrouter reads the deck from the function call`() =
        runTest {
            val outcome =
                openRouterAdvisor(
                    client(Captured()) {
                        json(openRouterAnswer())
                    },
                    "https://or.test",
                ).advise(request, access)

            outcome shouldBe Outcome.Success(expected)
        }

    @Test
    fun `openrouter reads a deck a model wrote as text when it ignored the tool`() =
        runTest {
            val text = "Here is the deck:\n```json\n$deckJson\n```"
            val body = """{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(text)}}}]}"""

            val outcome =
                openRouterAdvisor(
                    client(Captured()) { json(body) },
                    "https://or.test",
                ).advise(request, access)

            outcome shouldBe Outcome.Success(expected)
        }

    @Test
    fun `openrouter tells the model what was wrong with its earlier deck`() =
        runTest {
            val captured = Captured()
            val feedback = AdviceFeedback(expected, listOf("Magmar: the player owns 1 copy but the deck uses 2"))

            openRouterAdvisor(client(captured) { json(openRouterAnswer()) }, "https://or.test")
                .advise(request.copy(feedback = listOf(feedback)), access)

            val messages = captured.body["messages"]!!.jsonArray
            messages.map { it.jsonObject["role"]!!.jsonPrimitive.content } shouldBe
                listOf("system", "user", "assistant", "tool")
            val assistant = messages[2].jsonObject["tool_calls"] as JsonArray
            assistant
                .single()
                .jsonObject["id"]!!
                .jsonPrimitive.content shouldBe
                messages[3].jsonObject["tool_call_id"]!!.jsonPrimitive.content
            messages[3].jsonObject["content"]!!.jsonPrimitive.content shouldContain "owns 1 copy"
        }

    @Test
    fun `openrouter maps error answers`() =
        runTest {
            val invalid =
                openRouterAdvisor(
                    client(Captured()) {
                        json("""{"error":{"message":"No auth","code":401}}""", HttpStatusCode.Unauthorized)
                    },
                    "https://x",
                ).advise(request, access)
            val credits =
                openRouterAdvisor(
                    client(Captured()) {
                        json(
                            """{"error":{"message":"Insufficient credits","code":402}}""",
                            HttpStatusCode.PaymentRequired,
                        )
                    },
                    "https://x",
                ).advise(request, access)
            val busy =
                openRouterAdvisor(
                    client(Captured()) {
                        json(
                            """{"error":{"message":"Rate limit exceeded","code":429}}""",
                            HttpStatusCode.TooManyRequests,
                        )
                    },
                    "https://x",
                ).advise(request, access)
            val hidden =
                openRouterAdvisor(
                    client(Captured()) {
                        json("""{"error":{"message":"Provider returned error","code":429}}""")
                    },
                    "https://x",
                ).advise(request, access)

            invalid shouldBe Outcome.Failure(AdvisorFailure.InvalidKey)
            credits shouldBe Outcome.Failure(AdvisorFailure.Rejected("Insufficient credits"))
            busy shouldBe Outcome.Failure(AdvisorFailure.Busy("Rate limit exceeded"))
            hidden shouldBe Outcome.Failure(AdvisorFailure.Busy("Provider returned error"))
        }

    @Test
    fun `openrouter reports a missing connection and an answer without a deck`() =
        runTest {
            val offline =
                openRouterAdvisor(
                    client(Captured()) {
                        throw IOException("no network")
                    },
                    "https://x",
                ).advise(request, access)
            val noDeck =
                openRouterAdvisor(
                    client(Captured()) {
                        json("""{"choices":[{"message":{"content":"Sorry, no."}}]}""")
                    },
                    "https://x",
                ).advise(request, access)

            offline shouldBe Outcome.Failure(AdvisorFailure.NotReachable("no network"))
            (noDeck as Outcome.Failure).reason.javaClass shouldBe AdvisorFailure.Unusable::class.java
        }

    // ---- OpenAI and a server of the player's own

    private fun advisor(
        dialect: ChatDialect,
        client: HttpClient,
    ) = ChatCompletionsDeckAdvisor(client, dialect)

    @Test
    fun `openai sends max_completion_tokens and a short reasoning effort to a reasoning model`() =
        runTest {
            val captured = Captured()

            advisor(ChatDialect.OPENAI, client(captured) { json(openRouterAnswer()) })
                .advise(request, ProviderAccess("sk-1", "gpt-5-mini"))

            captured.request!!.url.toString() shouldBe "https://api.openai.com/v1/chat/completions"
            captured.request!!.headers["Authorization"] shouldBe "Bearer sk-1"
            captured.request!!.headers["X-Title"] shouldBe null
            captured.body.containsKey("max_tokens") shouldBe false
            captured.body["max_completion_tokens"]!!.jsonPrimitive.int shouldBe 12_000
            captured.body["reasoning_effort"]!!.jsonPrimitive.content shouldBe "low"
            captured.body.containsKey("reasoning") shouldBe false
        }

    @Test
    fun `openai leaves the reasoning effort out for a model that does not reason`() =
        runTest {
            val captured = Captured()

            advisor(ChatDialect.OPENAI, client(captured) { json(openRouterAnswer()) })
                .advise(request, ProviderAccess("sk-1", "gpt-4o"))

            captured.body.containsKey("reasoning_effort") shouldBe false
        }

    @Test
    fun `a server of the player's own is asked at their address without a key`() =
        runTest {
            val captured = Captured()

            val outcome =
                advisor(ChatDialect.OWN_SERVER, client(captured) { json(openRouterAnswer()) })
                    .advise(
                        request,
                        ProviderAccess("", "llama3.1", "https://pc.lan:1234/v1/", plainHttpAllowed = false),
                    )

            outcome.shouldBeInstanceOf<Outcome.Success<*>>()
            captured.request!!.url.toString() shouldBe "https://pc.lan:1234/v1/chat/completions"
            captured.request!!.headers.contains("Authorization") shouldBe false
            captured.body["max_tokens"]!!.jsonPrimitive.int shouldBe 12_000
            captured.body.containsKey("tool_choice") shouldBe false
            captured.body.containsKey("reasoning") shouldBe false
        }

    @Test
    fun `a server of the player's own gets the key when there is one`() =
        runTest {
            val captured = Captured()

            advisor(ChatDialect.OWN_SERVER, client(captured) { json(openRouterAnswer()) })
                .advise(request, ProviderAccess("lm-studio", "m", "https://pc.lan/v1"))

            captured.request!!.headers["Authorization"] shouldBe "Bearer lm-studio"
        }

    @Test
    fun `plain http is refused until the player allows it, and then used`() =
        runTest {
            val captured = Captured()
            val advisor = advisor(ChatDialect.OWN_SERVER, client(captured) { json(openRouterAnswer()) })

            val refused = advisor.advise(request, ProviderAccess("", "m", "http://192.168.2.5:11434/v1"))
            val allowed =
                advisor.advise(request, ProviderAccess("", "m", "http://192.168.2.5:11434/v1", plainHttpAllowed = true))

            (refused as Outcome.Failure).reason.javaClass shouldBe AdvisorFailure.Rejected::class.java
            captured.request!!.url.toString() shouldBe "http://192.168.2.5:11434/v1/chat/completions"
            allowed.shouldBeInstanceOf<Outcome.Success<*>>()
        }

    @Test
    fun `a server of the player's own without an address is rejected`() =
        runTest {
            val outcome =
                advisor(ChatDialect.OWN_SERVER, client(Captured()) { json(openRouterAnswer()) })
                    .advise(request, ProviderAccess("", "m"))

            (outcome as Outcome.Failure).reason.javaClass shouldBe AdvisorFailure.Rejected::class.java
        }

    private fun openRouterAdvisor(
        client: HttpClient,
        baseUrl: String,
        diagnostics: AdvisorDiagnostics = AdvisorDiagnostics.NONE,
    ) = ChatCompletionsDeckAdvisor(client, ChatDialect.OPENROUTER, baseUrl, diagnostics)
}
