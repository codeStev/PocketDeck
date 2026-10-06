package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [CardEntity::class, OwnedCardEntity::class, BackupOwnedCardEntity::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
internal abstract class PocketDeckDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    abstract fun ownedCardDao(): OwnedCardDao

    abstract fun backupDao(): BackupOwnedCardDao
}

/** Entry point for the composition root: builds the database without leaking Room types. */
object PocketDeckDatabases {
    private const val FILE_NAME = "pocketdeck.db"

    fun create(context: Context): PocketDeckStorage = storageOf(openFile(context, FILE_NAME))

    internal fun inMemory(context: Context): PocketDeckDatabase =
        Room.inMemoryDatabaseBuilder<PocketDeckDatabase>(context).setDriver(AndroidSQLiteDriver()).build()

    internal fun openFile(
        context: Context,
        fileName: String,
    ): PocketDeckDatabase {
        val builder = Room.databaseBuilder<PocketDeckDatabase>(context, fileName)
        return builder.setDriver(AndroidSQLiteDriver()).build()
    }

    private fun storageOf(database: PocketDeckDatabase) =
        PocketDeckStorage(
            catalogs = RoomCardCatalogRepository(database.cardDao()),
            collections = RoomCollectionRepository(database.ownedCardDao()),
            backup = RoomCollectionBackup(database.backupDao()),
        )
}
