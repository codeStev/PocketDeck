package dev.pocketdeck.domain.meta

/** A family of similar decks with how much it is played and how well it does. */
data class Archetype(
    val name: ArchetypeName,
    /** The decklists played, best placing first. */
    val decklists: List<MetaDecklist>,
    /** Share of all decks in the field, from 0.0 to 1.0. */
    val share: Double,
) {
    /**
     * Win rate over all games, pulled towards an even split while there are few games: two wins out of two is no
     * proof that a deck is unbeatable.
     */
    val winRate: Double
        get() {
            val games = decklists.sumOf { it.result.games }
            val wins = decklists.sumOf { it.result.wins + it.result.ties / 2.0 }
            return (wins + PRIOR_GAMES / 2.0) / (games + PRIOR_GAMES)
        }

    /** Popular and winning decks come first. */
    val strength: Double get() = winRate * share

    private companion object {
        const val PRIOR_GAMES = 20
    }
}

/** The state of the competitive field at one moment. */
data class MetaSnapshot(
    val archetypes: List<Archetype>,
) {
    companion object {
        val EMPTY = MetaSnapshot(emptyList())
    }
}

class ArchetypeAggregator {
    fun aggregate(decklists: List<MetaDecklist>): MetaSnapshot {
        if (decklists.isEmpty()) return MetaSnapshot.EMPTY
        val archetypes =
            decklists
                .groupBy { it.archetype }
                .map { (name, lists) ->
                    Archetype(
                        name,
                        lists.sortedBy { it.result.placing },
                        share = lists.size.toDouble() / decklists.size,
                    )
                }
        return MetaSnapshot(archetypes.sortedByDescending { it.share })
    }
}
