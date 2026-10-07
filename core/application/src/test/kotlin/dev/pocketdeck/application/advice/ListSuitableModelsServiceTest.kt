package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
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

    private fun service(vararg models: ListedModel) = ListSuitableModelsService { Outcome.Success(models.toList()) }

    private suspend fun ids(service: ListSuitableModelsService) =
        (service(AdvisorProvider.OPENROUTER) as Outcome.Success).value.map { it.id }

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
            val failing = ListSuitableModelsService { Outcome.Failure(AdvisorFailure.NotReachable("offline")) }

            failing(AdvisorProvider.OPENROUTER) shouldBe Outcome.Failure(AdvisorFailure.NotReachable("offline"))
        }

    @Test
    fun `leaves out the router that picks any free model`() =
        runTest {
            val service = service(model("openrouter/free"), model("good"))

            ids(service) shouldBe listOf("good")
        }
}
