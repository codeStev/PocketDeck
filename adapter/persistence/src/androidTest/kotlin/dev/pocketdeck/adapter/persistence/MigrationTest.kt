package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.domain.card.CardId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Builds a real version-1 database from the exported schema, fills it like the shipped v1 app would have, and
 * opens it with the current database class so Room runs the migration.
 */
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "migration-test.db"

    private companion object {
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"

        const val CHARIZARD_ROW =
            "INSERT INTO card VALUES ('a1-036','POKEMON','Charizard ex','Charizard ex#abc'," +
                "NULL,'FIRE','STAGE_2',180,'Charmeleon',2,NULL)"
    }

    @Before
    fun createVersionOneDatabase() {
        context.deleteDatabase(fileName)
        val schema = readExportedSchema(version = 1)
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(fileName).path)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                connection.execSQL(createTableSql(entity))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) connection.execSQL(setup.getString(index))
            // Room tells database versions apart through this pragma, exactly as in a database it created itself.
            connection.execSQL("PRAGMA user_version = 1")
            connection.execSQL(CHARIZARD_ROW)
            connection.execSQL("INSERT INTO owned_card VALUES ('a1-036', 3)")
        } finally {
            connection.close()
        }
    }

    @After
    fun deleteDatabase() {
        context.deleteDatabase(fileName)
    }

    @Test
    fun migratingToTheCurrentVersionKeepsTheCollectionAndTheCatalogInTheMainProfile() =
        runTest {
            val database = PocketDeckDatabases.openFile(context, fileName)
            try {
                val storage = PocketDeckDatabases.storageOf(database)
                val collection = storage.collections.current()
                val catalog = RoomCardCatalogRepository(database.cardDao()).current()

                storage.profiles
                    .observe()
                    .first()
                    .active.name.value shouldBe "Main"
                collection.copiesOf(CardId("a1-036")) shouldBe 3
                catalog.size shouldBe 1
                catalog.cards
                    .single()
                    .germanName.value shouldBe "Charizard ex"
            } finally {
                database.close()
            }
        }

    private fun createTableSql(entity: JSONObject): String =
        entity.getString("createSql").replace(TABLE_PLACEHOLDER, entity.getString("tableName"))

    private fun readExportedSchema(version: Int): JSONObject {
        val path = "dev.pocketdeck.adapter.persistence.PocketDeckDatabase/$version.json"
        val text =
            context.assets
                .open(path)
                .bufferedReader()
                .use { it.readText() }
        return JSONObject(text).getJSONObject("database")
    }
}
