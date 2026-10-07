package dev.pocketdeck.application.profile

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.profile.Profile
import dev.pocketdeck.domain.profile.ProfileId
import dev.pocketdeck.domain.profile.Profiles
import kotlinx.coroutines.flow.Flow

/** Why a profile could not be created or renamed. */
enum class ProfileNameProblem { BLANK, TAKEN }

/** Why a profile could not be removed. */
enum class RemoveProfileProblem { LAST_PROFILE, }

/** Inbound port: the profiles and the active one, kept up to date. */
fun interface ObserveProfiles {
    operator fun invoke(): Flow<Profiles>
}

/** Inbound port: add a game account. */
fun interface CreateProfile {
    suspend operator fun invoke(name: String): Outcome<Profile, ProfileNameProblem>
}

/** Inbound port: give a game account another name. */
fun interface RenameProfile {
    suspend operator fun invoke(
        id: ProfileId,
        name: String,
    ): Outcome<Unit, ProfileNameProblem>
}

/** Inbound port: work with another game account from now on. */
fun interface SwitchProfile {
    suspend operator fun invoke(id: ProfileId)
}

/** Inbound port: delete a game account together with its collection. The last profile cannot be deleted. */
fun interface DeleteProfile {
    suspend operator fun invoke(id: ProfileId): Outcome<Unit, RemoveProfileProblem>
}
