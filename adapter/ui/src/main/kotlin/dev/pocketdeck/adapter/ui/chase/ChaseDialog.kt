package dev.pocketdeck.adapter.ui.chase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdEmptyState
import dev.codestev.pocketdesign.components.PdFullScreenPanel
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R

private val ThumbWidth = 48.dp
private val ThumbHeight = 67.dp
private val ThumbCorner = 6.dp

/** Tells the player which cards to get next to finish tournament decks, and which packs hold them. */
@Composable
fun ChaseDialog(
    viewModel: ChaseViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PdFullScreenPanel(stringResource(R.string.chase_title), stringResource(R.string.decks_code_close), onClose) {
        ChaseContent(state)
    }
}

@Composable
internal fun ChaseContent(state: ChaseUiState) {
    if (state.cards.isEmpty()) {
        PdEmptyState(stringResource(R.string.chase_title), stringResource(R.string.chase_empty))
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PdSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        item {
            Text(
                stringResource(R.string.chase_hint),
                style = PocketDesign.typography.body,
                color = PocketDesign.colors.inkMuted,
            )
        }
        item { PdSectionHeader(stringResource(R.string.chase_cards)) }
        items(state.cards, key = { "card-${it.key}" }) { CardRow(it) }
        item { PdSectionHeader(stringResource(R.string.chase_packs)) }
        items(state.packs, key = { "pack-${it.name}" }) { PackRow(it) }
    }
}

@Composable
private fun CardRow(item: ChaseCardItem) {
    PdCard(Modifier.fillMaxWidth(), contentPadding = PdSpacing.space3) {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ThumbWidth, ThumbHeight).clip(RoundedCornerShape(ThumbCorner)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PdSpacing.space1)) {
                Text(item.name, style = PocketDesign.typography.titleSm, color = PocketDesign.colors.ink)
                EffectChip(item)
                Text(
                    packsText(item.packs),
                    style = PocketDesign.typography.bodySm,
                    color = PocketDesign.colors.inkMuted,
                )
            }
        }
    }
}

/** What the card does for the player: a finished deck is the good news, so it gets the success colour. */
@Composable
private fun EffectChip(item: ChaseCardItem) {
    if (item.decksFinished > 0) {
        PdChip(
            pluralStringResource(R.plurals.chase_finishes, item.decksFinished, item.decksFinished),
            kind = PdChipKind.Success,
        )
    } else {
        PdChip(pluralStringResource(R.plurals.chase_helps, item.decksHelped, item.decksHelped))
    }
}

@Composable
private fun packsText(packs: List<String>): String =
    if (packs.isEmpty()) stringResource(R.string.chase_no_pack) else packs.joinToString(", ")

@Composable
private fun PackRow(item: ChasePackItem) {
    PdCard(Modifier.fillMaxWidth(), contentPadding = PdSpacing.space3) {
        Text(item.name, style = PocketDesign.typography.titleSm, color = PocketDesign.colors.ink)
        val line =
            pluralStringResource(
                R.plurals.chase_pack_line,
                item.cardCount,
                item.cardCount,
                item.cardNames.joinToString(", "),
            )
        Text(line, style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)
    }
}
