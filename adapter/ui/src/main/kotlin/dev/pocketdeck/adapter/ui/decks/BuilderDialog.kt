package dev.pocketdeck.adapter.ui.decks

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdFullScreenPanel
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.thumbnailOf

private val ThumbWidth = 44.dp
private val ThumbHeight = 61.dp
private val ThumbCorner = 6.dp

/** Lets the player choose what a deck is built around, or leave it to the builder. */
@Composable
internal fun BuilderDialog(
    centrepieces: List<CentrepieceItem>,
    aiConfigured: Boolean,
    actions: DecksActions,
) {
    val onBuild = actions.onBuild
    PdFullScreenPanel(
        stringResource(R.string.decks_build_title),
        stringResource(R.string.decks_code_close),
        actions.onCloseBuilder,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PdSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        ) {
            item {
                Text(
                    stringResource(R.string.decks_build_hint),
                    style = PocketDesign.typography.body,
                    color = PocketDesign.colors.inkMuted,
                )
            }
            item {
                PdButton(
                    stringResource(R.string.decks_build_best),
                    { onBuild(null) },
                    Modifier.fillMaxWidth(),
                    leadingIcon = PdIcons.Plus,
                )
            }
            item { AskAiSection(aiConfigured, actions) }
            item { PdSectionHeader(stringResource(R.string.decks_build_around)) }
            items(centrepieces, key = { it.cardId }) { item -> CentrepieceRow(item, onBuild) }
        }
    }
}

/** Asks an AI for a deck, with a wish in the player's own words; needs a key for one of the AI services. */
@Composable
private fun AskAiSection(
    aiConfigured: Boolean,
    actions: DecksActions,
) {
    var wish by rememberSaveable { mutableStateOf("") }
    PdCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            Text(
                stringResource(R.string.decks_ai_title),
                style = PocketDesign.typography.titleMd,
                color = PocketDesign.colors.ink,
            )
            PdTextField(
                wish,
                { wish = it },
                Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.decks_ai_wish),
            )
            if (aiConfigured) {
                PdButton(
                    stringResource(R.string.decks_ai_ask),
                    { actions.onAskAi(wish) },
                    Modifier.fillMaxWidth(),
                    style = PdButtonStyle.Secondary,
                )
            } else {
                PdButton(
                    stringResource(R.string.decks_ai_set_up),
                    actions.onOpenSettings,
                    Modifier.fillMaxWidth(),
                    style = PdButtonStyle.Secondary,
                )
            }
        }
    }
}

@Composable
private fun CentrepieceRow(
    item: CentrepieceItem,
    onBuild: (String?) -> Unit,
) {
    PdCard(Modifier.fillMaxWidth(), contentPadding = PdSpacing.space3) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = thumbnailOf(item.artworkUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ThumbWidth, ThumbHeight).clip(RoundedCornerShape(ThumbCorner)),
            )
            Text(
                item.name,
                style = PocketDesign.typography.titleSm,
                color = PocketDesign.colors.ink,
                modifier = Modifier.weight(1f),
            )
            PdButton(stringResource(R.string.decks_build_use), {
                onBuild(item.cardId)
            }, style = PdButtonStyle.Tonal, size = PdButtonSize.Small)
        }
    }
}
