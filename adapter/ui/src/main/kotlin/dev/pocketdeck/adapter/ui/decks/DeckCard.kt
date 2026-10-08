package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCardFan
import dev.codestev.pocketdesign.components.PdCardFanPlaceholder
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdProgressMeter
import dev.codestev.pocketdesign.components.PdTextLink
import dev.codestev.pocketdesign.components.PdTintedCard
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PdTint
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.theme.tintKey
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.deck.SuggestionOrigin

/** What a deck card shows besides its deck: the opening odds, if asked for, and whether the deck is already kept. */
internal class DeckCardState(
    val opening: OpeningState?,
    val kept: Boolean,
)

private val HeroHeight = 124.dp
private val HeroCorner = 8.dp
private const val PERCENT_FULL = 100f

/**
 * A suggested deck: its key Pokémon on a tinted ground, whether it can be built, one filled action (the import code)
 * and quiet actions below. The origin chip says where the deck comes from, so tournament and made-up decks are never mixed up.
 */
@Composable
internal fun DeckCard(
    item: DeckItem,
    kind: DeckKind,
    card: DeckCardState,
    actions: DecksActions,
) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    var showOdds by rememberSaveable(item.deckId) { mutableStateOf(false) }
    val tint = PocketDesign.colors.tint((item.heroes.firstOrNull()?.type ?: EnergyType.COLORLESS).tintKey())
    PdTintedCard(tint, Modifier.fillMaxWidth(), art = { Hero(item, tint) }) {
        Title(item)
        BuildStatus(item)
        PdProgressMeter(
            item.ownedPercent / PERCENT_FULL,
            label = stringResource(R.string.decks_owned, item.ownedPercent),
        )
        Substitutes(item)
        item.rationale?.let {
            Text(it, style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)
        }
        PdTextLink(
            stringResource(if (expanded) R.string.decks_tap_collapse else R.string.decks_tap_expand),
            onClick = { expanded = !expanded },
        )
        if (expanded) item.cards.forEach { CardLine(it) }
        val plain = item.substitutes.isEmpty()
        val showCode = if (plain) R.string.decks_code_show else R.string.decks_code_show_substituted
        PdButton(stringResource(showCode), { actions.onShowCode(item.key) }, Modifier.fillMaxWidth())
        QuietActions(item, kind, card, showOdds, actions) { showOdds = !showOdds }
        if (showOdds) OpeningOddsContent(card.opening)
    }
}

@Composable
private fun BoxScope.Hero(
    item: DeckItem,
    tint: PdTint,
) {
    val art = item.heroes.map { hero -> @Composable { HeroArt(hero) } }
    if (art.isEmpty()) PdCardFanPlaceholder(tint) else PdCardFan(tint, art.map { slot -> { slot() } })
    // After the art, so the chip is drawn on top of it.
    PdChip(
        text = originText(item.origin),
        modifier = Modifier.align(Alignment.TopStart).padding(PdSpacing.space3),
        dot = PocketDesign.colors.tint(originTint(item.origin)).strong,
    )
}

@Composable
private fun HeroArt(hero: DeckHero) {
    AsyncImage(
        model = hero.artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Title(item: DeckItem) {
    val title =
        if (item.substitutes.isEmpty()) {
            item.title
        } else {
            "${item.title} · ${stringResource(R.string.decks_with_substitutes)}"
        }
    Text(title, style = PocketDesign.typography.titleMd, color = PocketDesign.colors.ink)
}

/** The answer the player looks for first: can I build this deck, or what is missing. */
@Composable
private fun BuildStatus(item: DeckItem) {
    if (item.missingCopies == 0) {
        PdChip(stringResource(R.string.decks_buildable), kind = PdChipKind.Success)
    } else {
        PdChip(
            pluralStringResource(R.plurals.decks_missing, item.missingCopies, item.missingCopies),
            kind = PdChipKind.Warning,
        )
    }
}

@Composable
private fun Substitutes(item: DeckItem) {
    if (item.substitutes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space1)) {
        Text(
            pluralStringResource(R.plurals.decks_substituted, item.substituteCopies, item.substituteCopies),
            style = PocketDesign.typography.titleSm,
            color = PocketDesign.colors.ink,
        )
        item.substitutes.forEach { SubstituteText(it) }
    }
}

@Composable
private fun QuietActions(
    item: DeckItem,
    kind: DeckKind,
    card: DeckCardState,
    showOdds: Boolean,
    actions: DecksActions,
    onToggleOdds: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PdButton(
            stringResource(if (showOdds) R.string.decks_opening_hide else R.string.decks_opening_show),
            {
                onToggleOdds()
                if (!showOdds) actions.onShowOpening(item.deckId)
            },
            Modifier.weight(1f),
            style = PdButtonStyle.Secondary,
            size = PdButtonSize.Small,
        )
        if (kind != DeckKind.SAVED) {
            PdButton(
                stringResource(if (card.kept) R.string.decks_saved_done else R.string.decks_save),
                { actions.onSave(item.key) },
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
                size = PdButtonSize.Small,
                enabled = !card.kept,
            )
        }
        ShareMenu(item, actions)
    }
    if (kind == DeckKind.SAVED) {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2), modifier = Modifier.fillMaxWidth()) {
            PdButton(
                stringResource(R.string.decks_rename),
                { actions.onRenameSaved(item) },
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
                size = PdButtonSize.Small,
            )
            PdButton(
                stringResource(R.string.decks_delete),
                { actions.onDeleteSaved(item) },
                Modifier.weight(1f),
                style = PdButtonStyle.Danger,
                size = PdButtonSize.Small,
            )
        }
    }
}

/** The share icon opens a small menu: the deck as text, or as a picture with the import QR. */
@Composable
private fun ShareMenu(
    item: DeckItem,
    actions: DecksActions,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PdIconButton(PdIcons.Share, stringResource(R.string.share_deck), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_text)) },
                onClick = {
                    open = false
                    actions.onShare(item.key, false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_image)) },
                onClick = {
                    open = false
                    actions.onShare(item.key, true)
                },
            )
        }
    }
}

@Composable
private fun originText(origin: SuggestionOrigin) =
    stringResource(
        when (origin) {
            SuggestionOrigin.META -> R.string.decks_origin_meta
            SuggestionOrigin.HEURISTIC -> R.string.decks_origin_heuristic
            SuggestionOrigin.AI -> R.string.decks_origin_ai
        },
    )

/** The dot of the origin chip: a second cue next to its words. */
private fun originTint(origin: SuggestionOrigin) =
    when (origin) {
        SuggestionOrigin.META -> "blue"
        SuggestionOrigin.HEURISTIC -> "green"
        SuggestionOrigin.AI -> "purple"
    }

@Composable
private fun SubstituteText(line: SubstituteLine) {
    val effects = line.effects.map { stringResource(it.label()) }.joinToString(", ")
    Column {
        Text(
            stringResource(R.string.decks_substitute_line, line.copies, line.replacement, line.original),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.ink,
        )
        if (effects.isNotEmpty()) {
            Text(
                stringResource(R.string.decks_substitute_alike, effects),
                style = PocketDesign.typography.bodySm,
                color = PocketDesign.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun CardLine(line: DeckCardLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.decks_card_line, line.copies, line.name),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (line.missingCopies > 0) {
            Text(
                stringResource(R.string.decks_card_missing, line.missingCopies),
                style = PocketDesign.typography.titleSm,
                color = PocketDesign.colors.danger,
            )
        }
    }
}
