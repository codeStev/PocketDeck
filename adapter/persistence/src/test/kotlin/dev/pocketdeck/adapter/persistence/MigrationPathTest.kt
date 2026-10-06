package dev.pocketdeck.adapter.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Room only notices a missing migration when the app opens an old database on a device, and then it crashes. The
 * exported schemas show which versions exist, so every step between them must be declared.
 */
class MigrationPathTest {
    private val schemaVersions =
        File("schemas/dev.pocketdeck.adapter.persistence.PocketDeckDatabase")
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .map { it.nameWithoutExtension.toInt() }
            .sorted()

    private val declaredSteps =
        Regex("""AutoMigration\(\s*from\s*=\s*(\d+),\s*to\s*=\s*(\d+)\s*,?\s*\)""")
            .findAll(File("src/main/kotlin/dev/pocketdeck/adapter/persistence/PocketDeckDatabase.kt").readText())
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toList()

    @Test
    fun `every schema version can be reached from the one before`() {
        declaredSteps shouldBe schemaVersions.zipWithNext()
    }

    @Test
    fun `the database is declared at the newest exported schema`() {
        val declared =
            Regex(
                """version\s*=\s*(\d+)""",
            ).find(File("src/main/kotlin/dev/pocketdeck/adapter/persistence/PocketDeckDatabase.kt").readText())
        declared!!.groupValues[1].toInt() shouldBe schemaVersions.last()
    }
}
