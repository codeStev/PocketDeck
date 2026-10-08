package dev.pocketdeck.adapter.ui.decks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.collection.label

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
    LaunchedEffect(request) {
        if (request.asImage) {
            val qr = request.code?.let { QrMatrix.of(it).toBitmap() }
            shareAsImage(context, deck.title, renderDeckImage(deck, labels, qr))
        } else {
            shareAsText(context, deck.title, shareText(deck, labels))
        }
        onHandled()
    }
}
