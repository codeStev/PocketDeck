package dev.pocketdeck.application.profile

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.InMemoryProfileRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ProfileServicesTest {
    private val profiles = InMemoryProfileRepository()
    private val create = CreateProfileService(profiles)
    private val rename = RenameProfileService(profiles)
    private val switch = SwitchProfileService(profiles)
    private val delete = DeleteProfileService(profiles)

    private suspend fun names() =
        profiles
            .observe()
            .first()
            .all
            .map { it.name.value }

    @Test
    fun `starts with one active profile`() =
        runTest {
            val observed = ObserveProfilesService(profiles)().first()

            observed.all.size shouldBe 1
            observed.active.name.value shouldBe "Main"
        }

    @Test
    fun `creates a profile with a trimmed name`() =
        runTest {
            val outcome = create("  Alt ")

            (outcome as Outcome.Success).value.name.value shouldBe "Alt"
            names() shouldBe listOf("Main", "Alt")
        }

    @Test
    fun `rejects blank and duplicate names, ignoring case`() =
        runTest {
            create("  ") shouldBe Outcome.Failure(ProfileNameProblem.BLANK)
            create("main") shouldBe Outcome.Failure(ProfileNameProblem.TAKEN)
            names() shouldBe listOf("Main")
        }

    @Test
    fun `a profile may be renamed to a spelling of its own name`() =
        runTest {
            val main = profiles.observe().first().active

            rename(main.id, "MAIN") shouldBe Outcome.Success(Unit)
            names() shouldBe listOf("MAIN")
        }

    @Test
    fun `renaming to another profile's name is rejected`() =
        runTest {
            val alt = (create("Alt") as Outcome.Success).value

            rename(alt.id, "Main") shouldBe Outcome.Failure(ProfileNameProblem.TAKEN)
        }

    @Test
    fun `switching changes the active profile`() =
        runTest {
            val alt = (create("Alt") as Outcome.Success).value

            switch(alt.id)

            profiles.observe().first().active shouldBe alt
        }

    @Test
    fun `deleting the active profile activates another one`() =
        runTest {
            val alt = (create("Alt") as Outcome.Success).value
            switch(alt.id)

            delete(alt.id) shouldBe Outcome.Success(Unit)

            profiles
                .observe()
                .first()
                .active.name.value shouldBe "Main"
        }

    @Test
    fun `the last profile cannot be deleted`() =
        runTest {
            val only = profiles.observe().first().active

            delete(only.id) shouldBe Outcome.Failure(RemoveProfileProblem.LAST_PROFILE)
            names() shouldBe listOf("Main")
        }
}
