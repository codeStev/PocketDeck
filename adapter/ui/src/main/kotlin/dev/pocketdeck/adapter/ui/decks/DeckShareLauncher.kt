package dev.pocketdeck.adapter.ui.decks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.collection.label
import dev.pocketdeck.adapter.ui.theme.tintKey
import dev.pocketdeck.domain.card.EnergyType

/** A deck the player chose to share, with its import code if the game can take the deck and how to send it. */
data class ShareRequest(
    val item: DeckItem,
    val code: String?,
    val asImage: Boolean,
)

/** Builds the text or the picture of the requested deck in the app's language, opens the share sheet and reports back. */
@Composable
internal fun ShareLauncher(
    request: ShareRequest,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val labels =
        ShareLabels(
            pokemon = stringResource(R.string.share_pokemon),
            trainers = stringResource(R.string.share_trainers),
            energy = stringResource(R.string.share_energy),
            importCode = stringResource(R.string.share_import_code),
            noCode = stringResource(R.string.share_no_code),
            scanHint = stringResource(R.string.share_scan_hint),
            footer = stringResource(R.string.share_footer),
        )
    val cards = request.item.cards.map { stringResource(R.string.decks_card_line, it.copies, it.name) to it.isPokemon }
    val energy = request.item.energyTypes.map { stringResource(it.label()) }
    val deck =
        SharedDeck(
            title = request.item.title,
            pokemon = cards.filter { it.second }.map { it.first },
            trainers = cards.filterNot { it.second }.map { it.first },
            energy = energy.joinToString(", "),
            code = request.code,
        )
    val tint =
        (
            request.item.heroes
                .firstOrNull()
                ?.type ?: EnergyType.COLORLESS
        ).tintKey()
    LaunchedEffect(request) {
        if (request.asImage) {
            val qr = request.code?.let { QrMatrix.of(it).toBitmap() }
            val heroes = request.item.heroes.mapNotNull { hero -> hero.artworkUrl?.let { loadBitmap(context, it) } }
            shareAsImage(context, deck.title, renderDeckImage(context, deck, labels, DeckImageStyle(tint), heroes, qr))
        } else {
            shareAsText(context, deck.title, shareText(deck, labels))
        }
        onHandled()
    }
}

/** A card's picture for the share image; null when it cannot be loaded, and the picture then goes without that card. */
private suspend fun loadBitmap(
    context: android.content.Context,
    url: String,
): android.graphics.Bitmap? {
    val request =
        ImageRequest
            .Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}
