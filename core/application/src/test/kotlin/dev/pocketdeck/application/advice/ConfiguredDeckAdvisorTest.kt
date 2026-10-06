package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.meta.MetaSnapshot
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ConfiguredDeckAdvisorTest {
    private val request =
        AdviceRequest(
            null,
            CardCatalog.EMPTY,
            Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
            MetaSnapshot.EMPTY,
        )
    private val advice = DeckAdvice("T", "R", emptyList(), listOf(EnergyType.FIRE))

    private class FakeStore(
        initial: AdvisorSettings,
    ) : AdvisorSettingsStore {
        private val state = MutableStateFlow(initial)

        override fun observe(): StateFlow<AdvisorSettings> = state

        override suspend fun current() = state.value

        override suspend fun save(settings: AdvisorSettings) {
            state.value = settings
        }
    }

    private class Recording(
        private val answer: Outcome<DeckAdvice, AdvisorFailure>,
    ) : ProviderDeckAdvisor {
        val accesses = mutableListOf<ProviderAccess>()

        override suspend fun advise(
            request: AdviceRequest,
            access: ProviderAccess,
        ): Outcome<DeckAdvice, AdvisorFailure> {
            accesses += access
            return answer
        }
    }

    private fun settings(vararg keys: Pair<AdvisorProvider, String?>) =
        AdvisorSettings().let { base -> keys.fold(base) { s, (provider, key) -> s.withKey(provider, key) } }

    private fun advisor(
        settings: AdvisorSettings,
        anthropic: ProviderDeckAdvisor = Recording(Outcome.Success(advice)),
        openRouter: ProviderDeckAdvisor = Recording(Outcome.Success(advice)),
    ) = ConfiguredDeckAdvisor(
        FakeStore(settings),
        mapOf(AdvisorProvider.ANTHROPIC to anthropic, AdvisorProvider.OPENROUTER to openRouter),
    )

    @Test
    fun `needs a key before it can advise`() =
        runTest {
            advisor(AdvisorSettings()).advise(request) shouldBe Outcome.Failure(AdvisorFailure.MissingKey)
        }

    @Test
    fun `uses the provider that has a key, with its key and its default model`() =
        runTest {
            val openRouter = Recording(Outcome.Success(advice))

            advisor(settings(AdvisorProvider.OPENROUTER to "sk-or"), openRouter = openRouter).advise(request)

            openRouter.accesses.single() shouldBe ProviderAccess("sk-or", AdvisorProvider.OPENROUTER.defaultModel)
        }

    @Test
    fun `uses the model the player chose`() =
        runTest {
            val anthropic = Recording(Outcome.Success(advice))
            val chosen =
                settings(
                    AdvisorProvider.ANTHROPIC to "sk-a",
                ).withModel(AdvisorProvider.ANTHROPIC, "claude-sonnet-5-5")

            advisor(chosen, anthropic = anthropic).advise(request)

            anthropic.accesses.single().model shouldBe "claude-sonnet-5-5"
        }

    @Test
    fun `tries the providers in the order the player set`() =
        runTest {
            val anthropic = Recording(Outcome.Success(advice))
            val openRouter = Recording(Outcome.Success(advice))
            val both =
                settings(AdvisorProvider.ANTHROPIC to "a", AdvisorProvider.OPENROUTER to "o")
                    .withOrder(listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC))

            advisor(both, anthropic, openRouter).advise(request)

            openRouter.accesses.size shouldBe 1
            anthropic.accesses.size shouldBe 0
        }

    @Test
    fun `falls back to the next provider when the first is busy`() =
        runTest {
            val busy = Recording(Outcome.Failure(AdvisorFailure.Busy("rate limited")))
            val second = Recording(Outcome.Success(advice))
            val both =
                settings(AdvisorProvider.ANTHROPIC to "a", AdvisorProvider.OPENROUTER to "o")
                    .withOrder(listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC))

            val outcome = advisor(both, anthropic = second, openRouter = busy).advise(request)

            outcome shouldBe Outcome.Success(advice)
            second.accesses.size shouldBe 1
        }

    @Test
    fun `reports the failure of the first provider when none works`() =
        runTest {
            val first = Recording(Outcome.Failure(AdvisorFailure.Busy("rate limited")))
            val second = Recording(Outcome.Failure(AdvisorFailure.InvalidKey))
            val both =
                settings(AdvisorProvider.ANTHROPIC to "a", AdvisorProvider.OPENROUTER to "o")
                    .withOrder(listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC))

            val outcome = advisor(both, anthropic = second, openRouter = first).advise(request)

            outcome shouldBe Outcome.Failure(AdvisorFailure.Busy("rate limited"))
        }

    @Test
    fun `ignores a blank key`() =
        runTest {
            advisor(settings(AdvisorProvider.OPENROUTER to "  ")).advise(request) shouldBe
                Outcome.Failure(AdvisorFailure.MissingKey)
        }

    @Test
    fun `settings know which providers can be used`() {
        val s = settings(AdvisorProvider.ANTHROPIC to "a")

        s.usable() shouldBe listOf(AdvisorProvider.ANTHROPIC)
        s.keyOf(AdvisorProvider.OPENROUTER) shouldBe null
    }

    @Test
    fun `removing a key makes the provider unusable again`() {
        val s = settings(AdvisorProvider.ANTHROPIC to "a").withKey(AdvisorProvider.ANTHROPIC, null)

        s.usable() shouldBe emptyList()
    }
}
