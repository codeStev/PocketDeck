package dev.pocketdeck.adapter.persistence

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Every database has this profile; a database from before profiles keeps its collection in it. */
private const val FIRST_PROFILE_ID = 1
private const val FIRST_PROFILE_NAME = "Main"

/** A new database starts with one profile, so the app never has to ask the player for an account first. */
internal object CreateFirstProfile : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO profile (id, name) VALUES ($FIRST_PROFILE_ID, '$FIRST_PROFILE_NAME')")
    }
}

/**
 * Version 5 had one global collection. The collection and its backup now belong to a profile, which changes
 * their primary key, and SQLite can only do that by copying into a new table.
 */
internal object AddProfilesMigration : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
        )
        connection.execSQL("INSERT INTO profile (id, name) VALUES ($FIRST_PROFILE_ID, '$FIRST_PROFILE_NAME')")
        listOf("owned_card", "owned_card_backup").forEach { table -> assignToFirstProfile(connection, table) }
    }

    private fun assignToFirstProfile(
        connection: SQLiteConnection,
        table: String,
    ) {
        connection.execSQL(
            "CREATE TABLE `${table}_new` (`profileId` INTEGER NOT NULL, `cardId` TEXT NOT NULL, " +
                "`copies` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `cardId`))",
        )
        connection.execSQL("INSERT INTO `${table}_new` SELECT $FIRST_PROFILE_ID, `cardId`, `copies` FROM `$table`")
        connection.execSQL("DROP TABLE `$table`")
        connection.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
    }
}
