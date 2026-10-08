package dev.pocketdeck.adapter.persistence

private const val PACK_SEPARATOR = '\u001E'

/** The packs of a card live in one text column; pack names never contain a control character. */
internal object PackCodec {
    fun encode(packs: List<String>): String? =
        packs.joinToString(PACK_SEPARATOR.toString()).takeIf { packs.isNotEmpty() }

    fun decode(text: String?): List<String> = text?.split(PACK_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
}
