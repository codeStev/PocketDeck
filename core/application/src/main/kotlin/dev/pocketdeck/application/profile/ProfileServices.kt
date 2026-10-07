package dev.pocketdeck.application.profile

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.profile.Profile
import dev.pocketdeck.domain.profile.ProfileId
import dev.pocketdeck.domain.profile.ProfileName
import dev.pocketdeck.domain.profile.Profiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveProfilesService(
    private val profiles: ProfileRepository,
) : ObserveProfiles {
    override fun invoke(): Flow<Profiles> = profiles.observe()
}

class CreateProfileService(
    private val profiles: ProfileRepository,
) : CreateProfile {
    override suspend fun invoke(name: String): Outcome<Profile, ProfileNameProblem> =
        when (val checked = checkedName(profiles, name, ignoring = null)) {
            is Outcome.Failure -> checked
            is Outcome.Success -> Outcome.Success(profiles.add(checked.value))
        }
}

class RenameProfileService(
    private val profiles: ProfileRepository,
) : RenameProfile {
    override suspend fun invoke(
        id: ProfileId,
        name: String,
    ): Outcome<Unit, ProfileNameProblem> =
        when (val checked = checkedName(profiles, name, ignoring = id)) {
            is Outcome.Failure -> checked
            is Outcome.Success -> Outcome.Success(profiles.rename(id, checked.value))
        }
}

class SwitchProfileService(
    private val profiles: ProfileRepository,
) : SwitchProfile {
    override suspend fun invoke(id: ProfileId) = profiles.activate(id)
}

class DeleteProfileService(
    private val profiles: ProfileRepository,
) : DeleteProfile {
    override suspend fun invoke(id: ProfileId): Outcome<Unit, RemoveProfileProblem> {
        if (profiles
                .observe()
                .first()
                .all.size <= 1
        ) {
            return Outcome.Failure(RemoveProfileProblem.LAST_PROFILE)
        }
        profiles.remove(id)
        return Outcome.Success(Unit)
    }
}

/** A name is usable when it is not blank and no other profile has it; [ignoring] is the profile being renamed. */
private suspend fun checkedName(
    profiles: ProfileRepository,
    raw: String,
    ignoring: ProfileId?,
): Outcome<ProfileName, ProfileNameProblem> {
    if (raw.isBlank()) return Outcome.Failure(ProfileNameProblem.BLANK)
    val name = ProfileName(raw.trim())
    val taken =
        profiles
            .observe()
            .first()
            .all
            .any { it.id != ignoring && it.name.sameAs(name) }
    return if (taken) Outcome.Failure(ProfileNameProblem.TAKEN) else Outcome.Success(name)
}
