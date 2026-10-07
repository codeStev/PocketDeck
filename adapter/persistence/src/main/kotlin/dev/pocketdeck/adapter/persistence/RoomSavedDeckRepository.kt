package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.savedeck.SavedDeckRepository
import dev.pocketdeck.domain.deck.SavedDeck
import dev.pocketdeck.domain.deck.SavedDeckContent
import dev.pocketdeck.domain.deck.SavedDeckId
import dev.pocketdeck.domain.deck.SuggestionOrigin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The saved decks of the active profile. */
internal class RoomSavedDeckRepository(
    private val dao: SavedDeckDao,
    private val activeProfile: Flow<Long>,
) : SavedDeckRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<SavedDeck>> =
        activeProfile.flatMapLatest { profile -> dao.observeAll(profile).map { rows -> rows.map(::toDeck) } }

    override suspend fun add(content: SavedDeckContent): SavedDeckId =
        SavedDeckId(dao.insert(toEntity(activeProfile.first(), content)))

    override suspend fun rename(
        id: SavedDeckId,
        title: String,
    ) = dao.rename(activeProfile.first(), id.value, title)

    override suspend fun delete(id: SavedDeckId) = dao.delete(activeProfile.first(), id.value)

    private fun toEntity(
        profile: Long,
        content: SavedDeckContent,
    ) = SavedDeckEntity(
        profileId = profile,
        title = content.title,
        origin = content.origin.name,
        cards = SavedDeckCodec.writeCards(content.cards),
        energyTypes = SavedDeckCodec.writeEnergy(content.energyTypes),
        rationale = content.rationale,
        savedAtEpochMillis = content.savedAt.toEpochMilli(),
    )

    private fun toDeck(row: SavedDeckEntity) =
        SavedDeck(
            SavedDeckId(row.id),
            SavedDeckContent(
                title = row.title,
                origin = SuggestionOrigin.valueOf(row.origin),
                cards = SavedDeckCodec.readCards(row.cards),
                energyTypes = SavedDeckCodec.readEnergy(row.energyTypes),
                rationale = row.rationale,
                savedAt = Instant.ofEpochMilli(row.savedAtEpochMillis),
            ),
        )
}
