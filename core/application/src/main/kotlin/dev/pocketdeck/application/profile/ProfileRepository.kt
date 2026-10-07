package dev.pocketdeck.application.profile

import dev.pocketdeck.domain.profile.Profile
import dev.pocketdeck.domain.profile.ProfileId
import dev.pocketdeck.domain.profile.ProfileName
import dev.pocketdeck.domain.profile.Profiles
import kotlinx.coroutines.flow.Flow

/**
 * Outbound port: the player's game accounts and which of them is active.
 *
 * There is always at least one profile. Collection and scan storage follow the active profile, so
 * switching it makes every observer of the collection emit the other account's cards.
 */
interface ProfileRepository {
    fun observe(): Flow<Profiles>

    suspend fun add(name: ProfileName): Profile

    suspend fun rename(
        id: ProfileId,
        name: ProfileName,
    )

    /** Removes the profile with its collection. If it was the active one, another profile becomes active. */
    suspend fun remove(id: ProfileId)

    suspend fun activate(id: ProfileId)
}
