package dev.pocketdeck.adapter.ui.welcome

import dev.pocketdeck.adapter.ui.scan.ConsentAccess
import dev.pocketdeck.application.consent.Consent
import dev.pocketdeck.application.consent.GrantConsentService
import dev.pocketdeck.application.consent.ObserveConsentService
import dev.pocketdeck.application.fakes.InMemoryConsentStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel(store: InMemoryConsentStore) =
        WelcomeViewModel(ConsentAccess(ObserveConsentService(store), GrantConsentService(store)))

    @Test
    fun `says nothing until storage has answered`() =
        runTest(dispatcher) {
            viewModel(InMemoryConsentStore()).state.value.agreed shouldBe null
        }

    @Test
    fun `a new player has not agreed yet`() =
        runTest(dispatcher) {
            val viewModel = viewModel(InMemoryConsentStore())

            advanceUntilIdle()

            viewModel.state.value.agreed shouldBe false
        }

    @Test
    fun `agreeing is remembered`() =
        runTest(dispatcher) {
            val store = InMemoryConsentStore()
            val viewModel = viewModel(store)
            advanceUntilIdle()

            viewModel.onAgree()
            advanceUntilIdle()

            viewModel.state.value.agreed shouldBe true
            viewModel(store)
                .also { advanceUntilIdle() }
                .state.value.agreed shouldBe true
        }

    @Test
    fun `agreeing to the tour does not agree to auto-scroll`() =
        runTest(dispatcher) {
            val store = InMemoryConsentStore()
            viewModel(store).onAgree()
            advanceUntilIdle()

            val autoScroll = store.observe(Consent.AUTO_SCROLL).first()
            autoScroll shouldBe false
        }
}
