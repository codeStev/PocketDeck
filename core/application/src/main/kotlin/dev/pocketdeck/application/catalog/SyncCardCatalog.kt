package dev.pocketdeck.application.catalog

import dev.pocketdeck.application.Outcome

data class CatalogSyncReport(
    val cardCount: Int,
)

sealed interface CatalogSyncFailure {
    data class SourceNotReachable(
        val reason: String,
    ) : CatalogSyncFailure

    data object EmptyCatalog : CatalogSyncFailure
}

/** Inbound port: refresh the local card catalog from its source. */
fun interface SyncCardCatalog {
    suspend operator fun invoke(): Outcome<CatalogSyncReport, CatalogSyncFailure>
}
