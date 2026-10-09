package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        CardEntity::class,
        OwnedCardEntity::class,
        BackupOwnedCardEntity::class,
        ProfileEntity::class,
        SavedDeckEntity::class,
    ],
    version = 10,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
    ],
)
internal abstract class PocketDeckDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    abstract fun ownedCardDao(): OwnedCardDao

    abstract fun backupDao(): BackupOwnedCardDao

    abstract fun profileDao(): ProfileDao

    abstract fun savedDeckDao(): SavedDeckDao
}

/** Entry point for the composition root: builds the database without leaking Room types. */
object PocketDeckDatabases {
    private const val FILE_NAME = "pocketdeck.db"

    fun create(context: Context): PocketDeckStorage =
        storageOf(openFile(context, FILE_NAME), SharedPreferencesActiveProfileMemory(context))

    internal fun inMemory(context: Context): PocketDeckDatabase =
        builder(Room.inMemoryDatabaseBuilder<PocketDeckDatabase>(context)).build()

    internal fun openFile(
        context: Context,
        fileName: String,
    ): PocketDeckDatabase = builder(Room.databaseBuilder<PocketDeckDatabase>(context, fileName)).build()

    private fun builder(builder: RoomDatabase.Builder<PocketDeckDatabase>) =
        builder
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(AddProfilesMigration)
            .addCallback(CreateFirstProfile)

    internal fun storageOf(
        database: PocketDeckDatabase,
        memory: ActiveProfileMemory = InMemoryActiveProfileMemory(),
    ): PocketDeckStorage {
        val profiles = RoomProfileRepository(database.profileDao(), memory)
        return PocketDeckStorage(
            catalogs = RoomCardCatalogRepository(database.cardDao()),
            collections = RoomCollectionRepository(database.ownedCardDao(), profiles.activeId),
            backup = RoomCollectionBackup(database.backupDao(), profiles.activeId),
            profiles = profiles,
            savedDecks = RoomSavedDeckRepository(database.savedDeckDao(), profiles.activeId),
        )
    }
}
