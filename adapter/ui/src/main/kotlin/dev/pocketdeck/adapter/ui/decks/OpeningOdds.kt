package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.components.PdMeterCell
import dev.codestev.pocketdesign.components.PdMeterRow
import dev.codestev.pocketdesign.components.PdMeterTable
import dev.codestev.pocketdesign.components.PdSegmentedTabs
import dev.codestev.pocketdesign.components.PdSpinner
import dev.codestev.pocketdesign.components.PdTextLink
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdSpinner(size = 20.dp, strokeWidth = 2.dp)
        Text(
            stringResource(R.string.decks_opening_loading),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
        )
    }
}

@Composable
private fun OddsTable(odds: OpeningOddsUi) {
    var first by rememberSaveable { mutableStateOf(true) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val rows = if (first) odds.goingFirst else odds.goingSecond
    val shown = if (showAll) rows else rows.take(TOP_ROWS)
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        Muted(R.string.decks_opening_intro)
        PdSegmentedTabs(
            listOf(stringResource(R.string.decks_opening_first), stringResource(R.string.decks_opening_second)),
            if (first) 0 else 1,
            { first = it == 0 },
            Modifier.fillMaxWidth(),
        )
        PdMeterTable(TURNS.map { it.toString() }, shown.flatMap { it.toMeterRows() })
        if (rows.size > TOP_ROWS) {
            PdTextLink(
                if (showAll) {
                    stringResource(R.string.decks_opening_show_fewer)
                } else {
                    pluralStringResource(R.plurals.decks_opening_show_all, rows.size, rows.size)
                },
                onClick = { showAll = !showAll },
            )
        }
        Muted(R.string.decks_opening_note)
    }
}

@Composable
private fun Muted(text: Int) =
    Text(stringResource(text), style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)

/** A Pokémon's row, followed by a quieter one when its deck drains its own energy and "has attacked" overstates it. */
@Composable
private fun OpeningRow.toMeterRows(): List<PdMeterRow> {
    val ever = hasAttacked.map { cell(it, emphasized = it >= LIKELY_PERCENT) }
    val sustained = attacksThatTurn?.map { cell(it, emphasized = false) }
    return listOfNotNull(
        PdMeterRow(name, ever),
        sustained?.let { PdMeterRow(stringResource(R.string.decks_opening_each_turn), it) },
    )
}

@Composable
private fun cell(
    percent: Int,
    emphasized: Boolean,
) = PdMeterCell(stringResource(R.string.decks_opening_percent, percent), percent / PERCENT_FULL, emphasized)

private val TURNS = 1..4
private const val LIKELY_PERCENT = 50
private const val TOP_ROWS = 3
private const val PERCENT_FULL = 100f
