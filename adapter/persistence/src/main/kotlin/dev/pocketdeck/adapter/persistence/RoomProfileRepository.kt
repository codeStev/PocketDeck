package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.profile.ProfileRepository
import dev.pocketdeck.domain.profile.Profile
import dev.pocketdeck.domain.profile.ProfileId
import dev.pocketdeck.domain.profile.ProfileName
import dev.pocketdeck.domain.profile.Profiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class RoomProfileRepository(
    private val dao: ProfileDao,
    private val memory: ActiveProfileMemory,
) : ProfileRepository {
    override fun observe(): Flow<Profiles> =
        combine(dao.observeAll(), memory.chosen) { rows, chosen ->
            // A removed or never chosen profile falls back to the oldest one rather than leaving the app without.
            val active = rows.firstOrNull { it.id == chosen } ?: rows.first()
            Profiles(rows.map(::toProfile), toProfile(active))
        }

    /** The id the collection storage follows. */
    val activeId: Flow<Long> = observe().map { it.active.id.value }.distinctUntilChanged()

    override suspend fun add(name: ProfileName): Profile {
        val id = dao.insert(ProfileEntity(name = name.value))
        return Profile(ProfileId(id), name)
    }

    override suspend fun rename(
        id: ProfileId,
        name: ProfileName,
    ) = dao.rename(id.value, name.value)

    override suspend fun remove(id: ProfileId) {
        require(observe().first().all.size > 1) { "The last profile cannot be removed" }
        dao.deleteWithCollection(id.value)
    }

    override suspend fun activate(id: ProfileId) = memory.choose(id.value)

    private fun toProfile(row: ProfileEntity) = Profile(ProfileId(row.id), ProfileName(row.name))
}
