package dev.pocketdeck.application.collection

class ObserveCollectionService(
    private val collections: CollectionRepository,
) : ObserveCollection {
    override fun invoke() = collections.observe()
}
