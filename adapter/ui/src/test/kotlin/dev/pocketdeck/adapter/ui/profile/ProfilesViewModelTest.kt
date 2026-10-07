package dev.pocketdeck.adapter.ui.profile

import dev.pocketdeck.application.fakes.InMemoryProfileRepository
import dev.pocketdeck.application.profile.CreateProfileService
import dev.pocketdeck.application.profile.DeleteProfileService
import dev.pocketdeck.application.profile.ObserveProfilesService
import dev.pocketdeck.application.profile.ProfileNameProblem
import dev.pocketdeck.application.profile.RenameProfileService
import dev.pocketdeck.application.profile.SwitchProfileService
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = InMemoryProfileRepository()
    private lateinit var viewModel: ProfilesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel =
            ProfilesViewModel(
                ObserveProfilesService(profiles),
                CreateProfileService(profiles),
                RenameProfileService(profiles),
                SwitchProfileService(profiles),
                DeleteProfileService(profiles),
            )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun names() =
        viewModel.state.value.profiles!!
            .all
            .map { it.name.value }

    @Test
    fun `shows the profiles and the active one`() =
        runTest(dispatcher) {
            advanceUntilIdle()

            names() shouldBe listOf("Main")
            viewModel.state.value.profiles!!
                .active.name.value shouldBe "Main"
        }

    @Test
    fun `adding an account closes the dialog and lists it`() =
        runTest(dispatcher) {
            viewModel.onAddRequested()
            viewModel.onNameSubmitted("Alt")
            advanceUntilIdle()

            names() shouldBe listOf("Main", "Alt")
            viewModel.state.value.dialog shouldBe null
        }

    @Test
    fun `a taken name keeps the dialog open with the problem`() =
        runTest(dispatcher) {
            viewModel.onAddRequested()
            viewModel.onNameSubmitted("main")
            advanceUntilIdle()

            viewModel.state.value.dialog shouldBe ProfileDialog.Add(ProfileNameProblem.TAKEN)
        }

    @Test
    fun `switching makes the other account active`() =
        runTest(dispatcher) {
            viewModel.onAddRequested()
            viewModel.onNameSubmitted("Alt")
            advanceUntilIdle()
            val alt =
                viewModel.state.value.profiles!!
                    .all
                    .last()

            viewModel.onSwitch(alt.id)
            advanceUntilIdle()

            viewModel.state.value.profiles!!
                .active shouldBe alt
        }

    @Test
    fun `deleting needs a confirmation and then removes the account`() =
        runTest(dispatcher) {
            viewModel.onAddRequested()
            viewModel.onNameSubmitted("Alt")
            advanceUntilIdle()
            val alt =
                viewModel.state.value.profiles!!
                    .all
                    .last()

            viewModel.onDeleteRequested(alt)
            viewModel.state.value.dialog shouldBe ProfileDialog.ConfirmDelete(alt)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            names() shouldBe listOf("Main")
            viewModel.state.value.dialog shouldBe null
        }
}
