package dev.pocketdeck.adapter.ui.settings

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import dev.pocketdeck.application.advice.AdvisorSettingsStore
import dev.pocketdeck.application.advice.ListSuitableModels
import dev.pocketdeck.application.advice.ListedModel
import dev.pocketdeck.application.advice.ObserveAdvisorSettingsService
import dev.pocketdeck.application.advice.SaveAdvisorSettingsService
import dev.pocketdeck.application.advice.Thinking
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private class FakeStore(
        initial: AdvisorSettings = AdvisorSettings(),
    ) : AdvisorSettingsStore {
        private val state = MutableStateFlow(initial)

        override fun observe(): StateFlow<AdvisorSettings> = state

        override suspend fun current() = state.value

        override suspend fun save(settings: AdvisorSettings) {
            state.value = settings
        }
    }

    private fun model(id: String) = ListedModel(id, "Name of $id", 262_144, true, true, Thinking.ANSWERS_DIRECTLY, null)

    private fun viewModel(
        store: FakeStore = FakeStore(),
        models: ListSuitableModels =
            ListSuitableModels { Outcome.Success(listOf(model("a/one:free"), model("b/two:free"))) },
    ) = SettingsViewModel(ObserveAdvisorSettingsService(store), SaveAdvisorSettingsService(store), models)

    @Test
    fun `shows what is saved`() =
        runTest(dispatcher) {
            val saved =
                AdvisorSettings()
                    .withKey(
                        AdvisorProvider.OPENROUTER,
                        "sk-or",
                    ).withModel(AdvisorProvider.OPENROUTER, "x/y:free")
            val viewModel = viewModel(FakeStore(saved))

            advanceUntilIdle()

            viewModel.state.value.form.keys[AdvisorProvider.OPENROUTER] shouldBe "sk-or"
            viewModel.state.value.form.models[AdvisorProvider.OPENROUTER] shouldBe "x/y:free"
            viewModel.state.value.changed shouldBe false
        }

    @Test
    fun `editing marks the settings as changed until they are saved`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onKeyChanged(AdvisorProvider.ANTHROPIC, "sk-ant")

            viewModel.state.value.changed shouldBe true
            viewModel.state.value.form.keys[AdvisorProvider.ANTHROPIC] shouldBe "sk-ant"
        }

    @Test
    fun `saving stores the trimmed key, the model and the order`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val viewModel = viewModel(store)
            advanceUntilIdle()
            viewModel.onKeyChanged(AdvisorProvider.OPENROUTER, "  sk-or  ")
            viewModel.onModelChanged(AdvisorProvider.OPENROUTER, "google/gemma-4-31b-it:free")
            viewModel.onMove(AdvisorProvider.OPENROUTER, -1)

            viewModel.onSave()
            advanceUntilIdle()

            store.current().keyOf(AdvisorProvider.OPENROUTER) shouldBe "sk-or"
            store.current().modelOf(AdvisorProvider.OPENROUTER) shouldBe "google/gemma-4-31b-it:free"
            store.current().order.first() shouldBe AdvisorProvider.OPENROUTER
            viewModel.state.value.changed shouldBe false
            viewModel.state.value.justSaved shouldBe true
        }

    @Test
    fun `an empty key removes the provider`() =
        runTest(dispatcher) {
            val store = FakeStore(AdvisorSettings().withKey(AdvisorProvider.ANTHROPIC, "sk-ant"))
            val viewModel = viewModel(store)
            advanceUntilIdle()

            viewModel.onKeyChanged(AdvisorProvider.ANTHROPIC, "")
            viewModel.onSave()
            advanceUntilIdle()

            store.current().usable() shouldBe emptyList()
        }

    @Test
    fun `a provider can be moved earlier or later but not out of the list`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            val first =
                viewModel.state.value.form.order
                    .first()

            viewModel.onMove(first, -1)
            viewModel.state.value.form.order
                .first() shouldBe first

            viewModel.onMove(first, 1)
            viewModel.state.value.form.order
                .last() shouldBe first
        }

    @Test
    fun `editing again after saving hides the saved note`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onSave()
            advanceUntilIdle()

            viewModel.onModelChanged(AdvisorProvider.OPENROUTER, "x")

            viewModel.state.value.justSaved shouldBe false
        }

    @Test
    fun `lists the suitable models when the picker opens`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onOpenModelPicker(AdvisorProvider.OPENROUTER)
            advanceUntilIdle()

            val picker = viewModel.state.value.modelPicker!!
            picker.provider shouldBe AdvisorProvider.OPENROUTER
            (picker.content as ModelPickerContent.Loaded).models.map { it.id } shouldBe
                listOf("a/one:free", "b/two:free")
        }

    @Test
    fun `says so when the list cannot be loaded`() =
        runTest(dispatcher) {
            val viewModel = viewModel(models = { Outcome.Failure(AdvisorFailure.NotReachable("offline")) })
            advanceUntilIdle()

            viewModel.onOpenModelPicker(AdvisorProvider.OPENROUTER)
            advanceUntilIdle()

            viewModel.state.value.modelPicker!!
                .content shouldBe ModelPickerContent.Failed("offline")
        }

    @Test
    fun `choosing models writes them in the order chosen and marks the settings as changed`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "b/two:free")
            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "a/one:free")

            viewModel.state.value.form.models[AdvisorProvider.OPENROUTER] shouldBe "b/two:free, a/one:free"
            viewModel.state.value.changed shouldBe true
        }

    @Test
    fun `choosing a model again takes it out`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "a/one:free")
            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "b/two:free")

            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "a/one:free")

            viewModel.state.value.form.models[AdvisorProvider.OPENROUTER] shouldBe "b/two:free"
            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "b/two:free")
            viewModel.state.value.form.models
                .containsKey(AdvisorProvider.OPENROUTER) shouldBe false
        }

    @Test
    fun `adds to the models the player already wrote`() =
        runTest(dispatcher) {
            val saved = AdvisorSettings().withModel(AdvisorProvider.OPENROUTER, "my/model")
            val viewModel = viewModel(FakeStore(saved))
            advanceUntilIdle()

            viewModel.onToggleModel(AdvisorProvider.OPENROUTER, "a/one:free")

            viewModel.state.value.form.models[AdvisorProvider.OPENROUTER] shouldBe "my/model, a/one:free"
        }

    @Test
    fun `closing the picker hides it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onOpenModelPicker(AdvisorProvider.OPENROUTER)
            advanceUntilIdle()

            viewModel.onCloseModelPicker()

            viewModel.state.value.modelPicker shouldBe null
        }
}
