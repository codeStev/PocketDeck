package dev.pocketdeck.adapter.llm

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdviceFormat
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.DeckAdvice
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.meta.DecklistEntry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The tool through which an advisor hands in its deck, and how its answer is read. Both providers use the same one. */
internal object DeckTool {
    private val lenient = Json { ignoreUnknownKeys = true }

    /** The JSON schema of the tool's input. */
    val schema: JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject(AdviceFormat.TITLE) { put("type", "string") }
                putJsonObject(AdviceFormat.RATIONALE) {
                    put("type", "string")
                    put("description", "Why these cards work together, in a few sentences.")
                }
                putJsonObject(AdviceFormat.CARDS) {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject(AdviceFormat.CARD_ID) { put("type", "string") }
                            putJsonObject(AdviceFormat.CARD_COUNT) { put("type", "integer") }
                        }
                        putJsonArray("required") {
                            add(JsonPrimitive(AdviceFormat.CARD_ID))
                            add(JsonPrimitive(AdviceFormat.CARD_COUNT))
                        }
                    }
                }
                putJsonObject(AdviceFormat.ENERGY) {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "string")
                        putJsonArray(
                            "enum",
                        ) { AdviceFormat.selectableEnergy.forEach { add(JsonPrimitive(AdviceFormat.energyName(it))) } }
                    }
                }
            }
            putJsonArray("required") {
                listOf(AdviceFormat.TITLE, AdviceFormat.CARDS, AdviceFormat.ENERGY).forEach { add(JsonPrimitive(it)) }
            }
        }

    /** An earlier answer written the way the tool takes it, so it can be shown to the advisor again. */
    fun inputOf(advice: DeckAdvice): JsonObject =
        buildJsonObject {
            put(AdviceFormat.TITLE, advice.title)
            put(AdviceFormat.RATIONALE, advice.rationale)
            put(
                AdviceFormat.CARDS,
                buildJsonArray {
                    advice.entries.forEach { entry ->
                        add(
                            buildJsonObject {
                                put(AdviceFormat.CARD_ID, entry.cardId.value)
                                put(AdviceFormat.CARD_COUNT, entry.copies)
                            },
                        )
                    }
                },
            )
            put(
                AdviceFormat.ENERGY,
                buildJsonArray {
                    advice.energyTypes.forEach { add(JsonPrimitive(AdviceFormat.energyName(it))) }
                },
            )
        }

    fun parse(input: JsonObject): Outcome<DeckAdvice, AdvisorFailure> {
        val cards = (input[AdviceFormat.CARDS] as? JsonArray) ?: return unusable("The answer has no list of cards")
        val entries =
            cards.map { card ->
                val fields = card as? JsonObject ?: return unusable("A card entry is not an object")
                val id = fields[AdviceFormat.CARD_ID]?.jsonPrimitive?.contentOrNull
                val count =
                    fields[AdviceFormat.CARD_COUNT]?.jsonPrimitive?.let {
                        it.intOrNull
                            ?: it.contentOrNull?.trim()?.toIntOrNull()
                    }
                if (id.isNullOrBlank() || count == null || count < 1) return unusable("A card entry has no id or count")
                DecklistEntry(CardId(id.trim()), count)
            }
        val energy =
            (input[AdviceFormat.ENERGY] as? JsonArray).orEmpty().mapNotNull {
                AdviceFormat.energyOf(it.jsonPrimitive.content)
            }
        val title = input[AdviceFormat.TITLE]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE
        val rationale = input[AdviceFormat.RATIONALE]?.jsonPrimitive?.contentOrNull.orEmpty()
        return Outcome.Success(DeckAdvice(title, rationale, entries, energy))
    }

    /** For models that ignore the tool and write the deck out as text: the first JSON object in the text. */
    fun parseText(text: String): Outcome<DeckAdvice, AdvisorFailure> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return unusable("The answer contains no deck")
        return try {
            parse(lenient.parseToJsonElement(text.substring(start, end + 1)).jsonObject)
        } catch (failure: SerializationException) {
            unusable("The answer is not a readable deck: ${failure.message}")
        } catch (failure: IllegalArgumentException) {
            unusable("The answer is not a readable deck: ${failure.message}")
        }
    }

    private fun unusable(reason: String) = Outcome.Failure(AdvisorFailure.Unusable(reason))

    private const val DEFAULT_TITLE = "AI deck"
}
