package dev.pocketdeck.adapter.exchange

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.exchange.CollectionFile
import dev.pocketdeck.application.exchange.FileFormat
import dev.pocketdeck.application.exchange.FileProblem
import dev.pocketdeck.application.exchange.OwnedCardRow
import dev.pocketdeck.domain.card.CardId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class JsonCsvCollectionFileCodecTest {
    private val codec = JsonCsvCollectionFileCodec()
    private val file =
        CollectionFile(
            "Main",
            Instant.parse("2026-10-08T12:00:00Z"),
            listOf(
                OwnedCardRow(CardId("a1-001"), "Bulbasaur", 2),
                OwnedCardRow(CardId("a1-216"), "Poké \"Ball\", big", 1),
            ),
        )
    private val expected = mapOf("a1-001" to 2, "a1-216" to 1)

    private fun read(text: String) = (codec.decode(text) as Outcome.Success).value

    private fun problem(text: String) = (codec.decode(text) as Outcome.Failure).reason

    @Test
    fun `json survives a round trip`() {
        read(codec.encode(file, FileFormat.JSON)) shouldBe expected
    }

    @Test
    fun `the json names its format and version so a newer file can be told`() {
        val json = codec.encode(file, FileFormat.JSON)

        json.contains("\"format\": \"pocketdeck-collection\"") shouldBe true
        json.contains("\"version\": 1") shouldBe true
        problem(json.replace("\"version\": 1", "\"version\": 2")) shouldBe FileProblem.UnsupportedVersion(2)
    }

    @Test
    fun `csv survives a round trip including names with commas and quotes`() {
        val csv = codec.encode(file, FileFormat.CSV)

        csv.startsWith("${0xFEFF.toChar()}id,name,copies\n") shouldBe true
        read(csv) shouldBe expected
    }

    @Test
    fun `reads a csv saved by a spreadsheet with semicolons and only the columns it needs`() {
        read("copies;id\n3;a1-001\n1;a1-002\n") shouldBe mapOf("a1-001" to 3, "a1-002" to 1)
    }

    @Test
    fun `duplicate ids add up and rows with zero copies are dropped`() {
        read("id,copies\na1-001,1\na1-001,2\na1-002,0\n") shouldBe mapOf("a1-001" to 3)
    }

    @Test
    fun `refuses what is not a collection`() {
        problem("hello") shouldBe FileProblem.NotACollectionFile
        problem("") shouldBe FileProblem.NotACollectionFile
        problem("{\"cards\": []}") shouldBe FileProblem.NotACollectionFile
        problem("id,copies\na1-001,many\n") shouldBe FileProblem.NotACollectionFile
        problem("id,copies\n,2\n") shouldBe FileProblem.NotACollectionFile
        problem("{ not json") shouldBe FileProblem.NotACollectionFile
    }
}
