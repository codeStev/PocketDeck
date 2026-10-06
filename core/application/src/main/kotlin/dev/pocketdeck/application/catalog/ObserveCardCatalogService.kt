package dev.pocketdeck.application.catalog

class ObserveCardCatalogService(
    private val catalogs: CardCatalogRepository,
) : ObserveCardCatalog {
    override fun invoke() = catalogs.observe()
}
