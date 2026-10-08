package dev.pocketdeck.domain.opening

/** Reads who an effect may pick among the player's Pokémon in play: "1 of your benched [ L ] Pokémon", "Golem or Onix". */
internal object SlotFilterReader {
    fun read(phrase: String): SlotFilter? {
        if (phrase.startsWith("each ")) return null
        val text = phrase.removePrefix("1 of ").removePrefix("your ").removePrefix("the ")
        val onlyBench = text.startsWith("benched ")
        val onlyActive = text.startsWith("active ") || text.endsWith(" in the active spot")
        val noun = text.removePrefix("benched ").removePrefix("active ").removeSuffix(" in the active spot")
        val filter = CardFilterReader.read(noun) ?: return null
        return SlotFilter { card, isActive ->
            filter.matches(card) && (!onlyBench || !isActive) &&
                (!onlyActive || isActive)
        }
    }
}
