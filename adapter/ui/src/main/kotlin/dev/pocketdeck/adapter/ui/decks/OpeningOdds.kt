package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.opening.OpeningAnalysis
import dev.pocketdeck.domain.opening.PokemonOdds
import kotlin.math.roundToInt

/** One Pokémon's chances for turns 1–4, in whole percent. [attacksThatTurn] is set only where it falls short of [hasAttacked]. */
data class OpeningRow(
    val name: String,
    val hasAttacked: List<Int>,
    val attacksThatTurn: List<Int>?,
)

data class OpeningOddsUi(
    val goingFirst: List<OpeningRow>,
    val goingSecond: List<OpeningRow>,
)

sealed interface OpeningState {
    data object Loading : OpeningState

    data class Ready(
        val odds: OpeningOddsUi,
    ) : OpeningState
}

object OpeningListing {
    /** A Pokémon that attacks every turn once it can has equal numbers in both rows; only a draining deck needs the second. */
    private const val SUSTAINED_GAP_PERCENT = 10
    private const val PERCENT = 100.0

    fun build(
        analysis: OpeningAnalysis,
        language: Language,
    ) = OpeningOddsUi(rows(analysis.goingFirst, language), rows(analysis.goingSecond, language))

    private fun rows(
        odds: List<PokemonOdds>,
        language: Language,
    ): List<OpeningRow> =
        odds.map { pokemon ->
            val ever = pokemon.turns.map { percent(it.hasAttacked) }
            val onTurn = pokemon.turns.map { percent(it.attacksThatTurn) }
            val drains = ever.zip(onTurn).any { (total, that) -> total - that >= SUSTAINED_GAP_PERCENT }
            OpeningRow(pokemon.card.nameIn(language).value, ever, onTurn.takeIf { drains })
        }

    private fun percent(chance: Double) = (chance * PERCENT).roundToInt()
}

/** The "Opening odds" button of a deck; the table it opens is [OpeningOddsContent]. */
@Composable
internal fun OpeningOddsButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlinedButton(onToggle, modifier) {
        Text(stringResource(if (expanded) R.string.decks_opening_hide else R.string.decks_opening_show))
    }
}

/** What the player sees under the buttons once the odds are asked for: a spinner while it is worked out, then the table. */
@Composable
internal fun OpeningOddsContent(state: OpeningState?) {
    // The table opens below the buttons, often below the fold: scroll to it, first for the spinner, then for the table.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(state is OpeningState.Ready) { requester.bringIntoView() }
    Column(Modifier.bringIntoViewRequester(requester)) {
        when (state) {
            null, OpeningState.Loading -> Loading()
            is OpeningState.Ready -> OddsTable(state.odds)
        }
    }
}

@Composable
private fun Loading() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.decks_opening_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OddsTable(odds: OpeningOddsUi) {
    var first by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.decks_opening_intro), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(first, { first = true }, { Text(stringResource(R.string.decks_opening_first)) })
            FilterChip(!first, { first = false }, { Text(stringResource(R.string.decks_opening_second)) })
        }
        var showAll by rememberSaveable { mutableStateOf(false) }
        val rows = if (first) odds.goingFirst else odds.goingSecond
        TurnHeader()
        (if (showAll) rows else rows.take(TOP_ROWS)).forEach { PokemonRow(it) }
        if (rows.size > TOP_ROWS) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(
                    if (showAll) {
                        stringResource(R.string.decks_opening_show_fewer)
                    } else {
                        pluralStringResource(R.plurals.decks_opening_show_all, rows.size, rows.size)
                    },
                )
            }
        }
        Text(stringResource(R.string.decks_opening_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TurnHeader() {
    Row(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.decks_opening_turn),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(NAME_WEIGHT),
        )
        TURNS.forEach { Cell(it.toString(), MaterialTheme.typography.labelMedium, FontWeight.Normal) }
    }
}

@Composable
private fun PokemonRow(row: OpeningRow) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(NAME_WEIGHT))
            row.hasAttacked.forEach { Percent(it, bold = it >= LIKELY_PERCENT) }
        }
        row.attacksThatTurn?.let { sustained ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.decks_opening_each_turn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(NAME_WEIGHT),
                )
                sustained.forEach { Percent(it, bold = false, small = true) }
            }
        }
    }
}

/** A chance as text with a thin bar under it, so a column of them can be scanned without reading every number. */
@Composable
private fun RowScope.Percent(
    value: Int,
    bold: Boolean,
    small: Boolean = false,
) {
    Column(Modifier.weight(CELL_WEIGHT).padding(start = 4.dp), horizontalAlignment = Alignment.End) {
        Text(
            stringResource(R.string.decks_opening_percent, value),
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        if (!small) {
            LinearProgressIndicator(
                progress = { value / PERCENT_FULL },
                modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    style: TextStyle,
    weight: FontWeight,
) = Text(text, style = style, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(CELL_WEIGHT))

private val TURNS = 1..4
private const val NAME_WEIGHT = 2f
private const val CELL_WEIGHT = 1f
private const val LIKELY_PERCENT = 50
private const val TOP_ROWS = 3
private const val PERCENT_FULL = 100f
private val BAR_HEIGHT = 3.dp
