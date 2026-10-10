package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ListSuitableModelsServiceTest {
    private fun model(
        id: String,
        context: Int = 262_144,
        free: Boolean = true,
        tools: Boolean = true,
        thinking: Thinking = Thinking.ANSWERS_DIRECTLY,
    ) = ListedModel(id, id, context, free, tools, thinking, expiresOn = null)

    private fun service(vararg models: ListedModel) =
        ListSuitableModelsService { _, _, _ -> Outcome.Success(models.toList()) }

    private suspend fun ids(
        service: ListSuitableModelsService,
        request: ModelRequest = ModelRequest(AdvisorProvider.OPENROUTER),
    ) = (service(request) as Outcome.Success).value.map { it.id }

    @Test
    fun `keeps only free models that take tools and a long prompt`() =
        runTest {
            val service =
                service(
                    model("good"),
                    model("paid", free = false),
                    model("no-tools", tools = false),
                    model("short", context = 32_000),
                )

            ids(service) shouldBe listOf("good")
        }

    @Test
    fun `puts models that answer directly before those that think first`() =
        runTest {
            val service =
                service(
                    model("must-think", thinking = Thinking.MUST_THINK),
                    model("thinks", thinking = Thinking.THINKS_BY_DEFAULT),
                    model("direct", thinking = Thinking.ANSWERS_DIRECTLY),
                )

            ids(service) shouldBe listOf("direct", "thinks", "must-think")
        }

    @Test
    fun `among equals the larger context comes first, then the name`() =
        runTest {
            val service =
                service(model("b", context = 200_000), model("a", context = 200_000), model("big", context = 1_000_000))

            ids(service) shouldBe listOf("big", "a", "b")
        }

    @Test
    fun `passes on a failure to reach the catalog`() =
        runTest {
            val failing =
                ListSuitableModelsService {
                    _,
                    _,
                    _,
                    ->
                    Outcome.Failure(AdvisorFailure.NotReachable("offline"))
                }

            failing(ModelRequest(AdvisorProvider.OPENROUTER)) shouldBe
                Outcome.Failure(AdvisorFailure.NotReachable("offline"))
        }

    @Test
    fun `leaves out the router that picks any free model`() =
        runTest {
            val service = service(model("openrouter/free"), model("good"))

            ids(service) shouldBe listOf("good")
        }

    @Test
    fun `offers paid models too when asked, after the free ones`() =
        runTest {
            val paid = model("paid-a", free = false).copy(promptPrice = 1.0, completionPrice = 2.0)
            val service = service(paid, model("free-b"), model("paid-short", free = false, context = 8_000))

            ids(service, ModelRequest(AdvisorProvider.OPENROUTER, includePaid = true)) shouldBe
                listOf("free-b", "paid-a")
        }

    @Test
    fun `keeps Claude models as the service lists them, newest first`() =
        runTest {
            val service = service(model("claude-new", free = false), model("claude-old", free = false))

            ids(service, ModelRequest(AdvisorProvider.ANTHROPIC, apiKey = "k")) shouldBe
                listOf("claude-new", "claude-old")
        }

    @Test
    fun `hands the typed key to the catalog`() =
        runTest {
            var seen: String? = null
            val service =
                ListSuitableModelsService { _, key, _ ->
                    seen = key
                    Outcome.Success(emptyList())
                }

            service(ModelRequest(AdvisorProvider.ANTHROPIC, apiKey = "sk-ant"))

            seen shouldBe "sk-ant"
        }

    @Test
    fun `estimates what one deck costs`() {
        val paid =
            ListedModel(
                "m",
                "m",
                200_000,
                false,
                true,
                Thinking.ANSWERS_DIRECTLY,
                null,
                promptPrice = 3.0,
                completionPrice = 15.0,
            )

        // 22,000 tokens in at $3 per million, 3,000 out at $15 per million.
        ModelCost.perDeck(paid) shouldBe (0.066 + 0.045 plusOrMinus 1e-9)
        ModelCost.perDeck(model("free")) shouldBe 0.0
        ModelCost.perDeck(paid.copy(promptPrice = null)) shouldBe null
    }

    @Test
    fun `a paid model without a stated price is left out, as the routers are`() =
        runTest {
            val priced = model("priced", free = false).copy(promptPrice = 1.0, completionPrice = 2.0)
            val service = service(priced, model("router", free = false))

            ids(service, ModelRequest(AdvisorProvider.OPENROUTER, includePaid = true)) shouldBe listOf("priced")
        }

    @Test
    fun `lists every model of a server of the player's own as it comes and hands over the address`() =
        runTest {
            var seenAddress: String? = null
            val service =
                ListSuitableModelsService { _, _, address ->
                    seenAddress = address
                    Outcome.Success(
                        listOf(
                            model("qwen3", context = 0, free = false, tools = false),
                            model("llama3.1", context = 0, free = false),
                        ),
                    )
                }

            val listed =
                (
                    service(
                        ModelRequest(AdvisorProvider.OWN_SERVER, address = "http://pc:11434/v1"),
                    ) as Outcome.Success
                ).value

            listed.map { it.id } shouldBe listOf("qwen3", "llama3.1")
            seenAddress shouldBe "http://pc:11434/v1"
        }
}
