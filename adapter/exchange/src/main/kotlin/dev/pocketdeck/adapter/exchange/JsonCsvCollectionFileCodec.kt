package dev.pocketdeck.adapter.exchange

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.exchange.CollectionFile
import dev.pocketdeck.application.exchange.CollectionFileCodec
import dev.pocketdeck.application.exchange.FileFormat
import dev.pocketdeck.application.exchange.FileProblem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Writes the collection as JSON (for PocketDeck itself, with a format name and version) or as CSV (for spreadsheets), and
 * reads both back. CSV is read with a comma or a semicolon, the way spreadsheets in other languages save it.
 */
class JsonCsvCollectionFileCodec : CollectionFileCodec {
    private val pretty = Json { prettyPrint = true }

    override fun encode(
        file: CollectionFile,
        format: FileFormat,
    ): String =
        when (format) {
            FileFormat.JSON -> encodeJson(file)
            FileFormat.CSV -> encodeCsv(file)
        }

    override fun decode(text: String): Outcome<Map<String, Int>, FileProblem> {
        val plain = text.removePrefix(BOM).trim()
        return if (plain.startsWith("{")) decodeJson(plain) else decodeCsv(plain)
    }

    private fun encodeJson(file: CollectionFile): String =
        pretty.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put(FORMAT_KEY, FORMAT_NAME)
                put(VERSION_KEY, VERSION)
                put("account", file.accountName)
                put("exportedAt", file.exportedAt.toString())
                put(
                    "cards",
                    buildJsonArray {
                        file.rows.forEach { row ->
                            add(
                                buildJsonObject {
                                    put("id", row.id.value)
                                    put("name", row.name)
                                    put("copies", row.copies)
                                },
                            )
                        }
                    },
                )
            },
        )

    private fun decodeJson(text: String): Outcome<Map<String, Int>, FileProblem> {
        val root = parseObject(text) ?: return NOT_A_FILE
        if (root[FORMAT_KEY]?.jsonPrimitive?.contentOrNull != FORMAT_NAME) return NOT_A_FILE
        val version = root[VERSION_KEY]?.jsonPrimitive?.intOrNull ?: return NOT_A_FILE
        if (version > VERSION) return Outcome.Failure(FileProblem.UnsupportedVersion(version))
        val cards = root["cards"] as? JsonArray ?: return NOT_A_FILE
        val copies = LinkedHashMap<String, Int>()
        for (entry in cards) {
            val card = entry as? JsonObject ?: return NOT_A_FILE
            val id =
                card["id"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    .orEmpty()
            val count = (card["copies"] as? JsonPrimitive)?.intOrNull ?: return NOT_A_FILE
            if (id.isEmpty() || count < 0) return NOT_A_FILE
            if (count > 0) copies.merge(id, count, Int::plus)
        }
        return Outcome.Success(copies)
    }

    private fun parseObject(text: String): JsonObject? =
        try {
            Json.parseToJsonElement(text).jsonObject
        } catch (failure: SerializationException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }

    private fun encodeCsv(file: CollectionFile): String =
        buildString {
            // The byte order mark makes Excel read the names (Poké Ball) as UTF-8.
            append(BOM)
            append("id,name,copies\n")
            file.rows.forEach {
                append(field(it.id.value))
                    .append(',')
                    .append(field(it.name))
                    .append(',')
                    .append(it.copies)
                    .append('\n')
            }
        }

    private fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun decodeCsv(text: String): Outcome<Map<String, Int>, FileProblem> {
        val lines = text.lines().filter { it.isNotBlank() }
        val header = lines.firstOrNull() ?: return NOT_A_FILE
        val delimiter = if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
        val columns = split(header, delimiter).map { it.trim().lowercase() }
        val idColumn = columns.indexOf("id")
        val copiesColumn = columns.indexOf("copies")
        if (idColumn < 0 || copiesColumn < 0) return NOT_A_FILE
        val copies = LinkedHashMap<String, Int>()
        for (line in lines.drop(1)) {
            val cells = split(line, delimiter)
            val id = cells.getOrNull(idColumn)?.trim().orEmpty()
            val count = cells.getOrNull(copiesColumn)?.trim()?.toIntOrNull() ?: return NOT_A_FILE
            if (id.isEmpty() || count < 0) return NOT_A_FILE
            if (count > 0) copies.merge(id, count, Int::plus)
        }
        return Outcome.Success(copies)
    }

    /** Splits one line into cells; a cell in quotes may hold the delimiter and doubled quotes. */
    private fun split(
        line: String,
        delimiter: Char,
    ): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }

                char == '"' -> {
                    quoted = !quoted
                }

                char == delimiter && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }

                else -> {
                    current.append(char)
                }
            }
            index++
        }
        cells += current.toString()
        return cells
    }

    private companion object {
        /** Written as a code so a formatter cannot turn the invisible character into an empty string. */
        val BOM = 0xFEFF.toChar().toString()
        const val FORMAT_KEY = "format"
        const val FORMAT_NAME = "pocketdeck-collection"
        const val VERSION_KEY = "version"
        const val VERSION = 1
        val NOT_A_FILE = Outcome.Failure(FileProblem.NotACollectionFile)
    }
}
