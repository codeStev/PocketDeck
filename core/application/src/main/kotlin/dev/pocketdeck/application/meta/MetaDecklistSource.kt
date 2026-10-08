package dev.pocketdeck.application.meta

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.meta.MetaDecklist

sealed interface MetaSourceFailure {
    data class NotReachable(
        val reason: String,
    ) : MetaSourceFailure

    /** The source is limiting how often it is asked (HTTP 429); trying again a little later works. */
    data object RateLimited : MetaSourceFailure

    /** The source answered, but with nothing that can be used as a decklist. */
    data object NoDecklists : MetaSourceFailure
}

/** Outbound port: where the decks that did well in recent tournaments come from. */
fun interface MetaDecklistSource {
    suspend fun recentDecklists(): Outcome<List<MetaDecklist>, MetaSourceFailure>
}
