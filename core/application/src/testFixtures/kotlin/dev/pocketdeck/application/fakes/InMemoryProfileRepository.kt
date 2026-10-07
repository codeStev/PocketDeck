package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.profile.ProfileRepository
import dev.pocketdeck.domain.profile.Profile
import dev.pocketdeck.domain.profile.ProfileId
import dev.pocketdeck.domain.profile.ProfileName
import dev.pocketdeck.domain.profile.Profiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryProfileRepository(
    first: ProfileName = ProfileName("Main"),
) : ProfileRepository {
    private data class State(
        val all: List<Profile>,
        val active: ProfileId,
        val nextId: Long,
    )

    private val state = MutableStateFlow(State(listOf(Profile(ProfileId(1), first)), ProfileId(1), nextId = 2))

    override fun observe(): Flow<Profiles> = state.map { s -> Profiles(s.all, s.all.first { it.id == s.active }) }

    override suspend fun add(name: ProfileName): Profile {
        val profile = Profile(ProfileId(state.value.nextId), name)
        state.value = state.value.copy(all = state.value.all + profile, nextId = state.value.nextId + 1)
        return profile
    }

    override suspend fun rename(
        id: ProfileId,
        name: ProfileName,
    ) {
        state.value = state.value.copy(all = state.value.all.map { if (it.id == id) it.copy(name = name) else it })
    }

    override suspend fun remove(id: ProfileId) {
        val remaining = state.value.all.filterNot { it.id == id }
        val active = if (state.value.active == id) remaining.first().id else state.value.active
        state.value = state.value.copy(all = remaining, active = active)
    }

    override suspend fun activate(id: ProfileId) {
        state.value = state.value.copy(active = id)
    }
}
