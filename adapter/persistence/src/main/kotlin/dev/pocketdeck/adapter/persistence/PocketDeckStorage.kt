package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.profile.ProfileRepository
import dev.pocketdeck.application.savedeck.SavedDeckRepository

/** The persistence ports as implemented by the local database. */
class PocketDeckStorage(
    val catalogs: CardCatalogRepository,
    val collections: CollectionRepository,
    val backup: CollectionBackup,
    val profiles: ProfileRepository,
    val savedDecks: SavedDeckRepository,
)
