package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.domain.card.Attack

private const val RECORD_SEPARATOR = '\u001E'
private const val FIELD_SEPARATOR = '\u001F'
private const val FIELDS_PER_ATTACK = 4

/** Attacks live in one text column: control characters never occur in card texts, so they keep the fields apart. */
internal object AttackCodec {
    fun encode(attacks: List<Attack>): String? =
        attacks
            .joinToString(RECORD_SEPARATOR.toString()) {
                listOf(it.name, it.cost, it.damage?.toString().orEmpty(), it.effect.orEmpty())
                    .joinToString(FIELD_SEPARATOR.toString())
            }.takeIf { attacks.isNotEmpty() }

    fun decode(text: String?): List<Attack> =
        text
            ?.split(RECORD_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.map { record ->
                val fields = record.split(FIELD_SEPARATOR)
                check(fields.size == FIELDS_PER_ATTACK) { "Corrupt attack record '$record'" }
                Attack(fields[0], fields[1], fields[2].toIntOrNull(), fields[3].ifEmpty { null })
            }.orEmpty()
}
