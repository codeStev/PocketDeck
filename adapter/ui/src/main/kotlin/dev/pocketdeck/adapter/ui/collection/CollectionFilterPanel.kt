package dev.pocketdeck.adapter.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdFilterChip
import dev.codestev.pocketdesign.components.PdFullScreenPanel
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.domain.card.Rarity
import dev.pocketdeck.domain.card.TrainerKind

/** Sorting and the filters that are too many for the screen itself: set, rarity and type. */
@Composable
internal fun CollectionFilterPanel(
    filter: CollectionFilter,
    options: FilterOptions,
    onChange: (CollectionFilter) -> Unit,
    onClose: () -> Unit,
) {
    PdFullScreenPanel(
        stringResource(R.string.collection_filter_title),
        stringResource(R.string.decks_code_close),
        onClose,
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PdSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
        ) {
            SortChoices(filter, onChange)
            Choices(R.string.collection_filter_set, options.sets, filter.sets, { it.uppercase() }) {
                onChange(filter.copy(sets = it))
            }
            Choices(
                R.string.collection_filter_rarity,
                options.rarities,
                filter.rarities,
                { stringResource(it.label()) },
            ) {
                onChange(filter.copy(rarities = it))
            }
            Choices(R.string.collection_filter_type, options.types, filter.types, { stringResource(it.label()) }) {
                onChange(filter.copy(types = it))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(PdSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        ) {
            PdButton(stringResource(R.string.collection_done), onClose, Modifier.fillMaxWidth())
            PdButton(
                stringResource(R.string.collection_filter_clear),
                { onChange(filter.withoutPanelChoices()) },
                Modifier.fillMaxWidth(),
                style = PdButtonStyle.Text,
                enabled = filter.chosenCount > 0,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChoices(
    filter: CollectionFilter,
    onChange: (CollectionFilter) -> Unit,
) {
    Section(R.string.collection_filter_sort) {
        CollectionSort.entries.forEach { sort ->
            PdFilterChip(stringResource(sort.label()), filter.sort == sort, { onChange(filter.copy(sort = sort)) })
        }
    }
}

/** A group of chips; a chosen one is added to [chosen], tapping it again removes it. */
@Composable
private fun <T> Choices(
    title: Int,
    options: List<T>,
    chosen: Set<T>,
    label: @Composable (T) -> String,
    onChange: (Set<T>) -> Unit,
) {
    if (options.isEmpty()) return
    Section(title) {
        options.forEach { option ->
            PdFilterChip(
                label(option),
                option in chosen,
                { on -> onChange(if (on) chosen + option else chosen - option) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(
    title: Int,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space1)) {
        Text(stringResource(title), style = PocketDesign.typography.titleSm, color = PocketDesign.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2)) { chips() }
    }
}

private fun CollectionSort.label() =
    when (this) {
        CollectionSort.NUMBER -> R.string.collection_sort_number
        CollectionSort.NAME -> R.string.collection_sort_name
        CollectionSort.COPIES -> R.string.collection_sort_copies
        CollectionSort.RARITY -> R.string.collection_sort_rarity
    }

private fun CardType.label() =
    when (this) {
        is CardType.OfEnergy -> energy.label()
        is CardType.OfTrainer -> kind.label()
    }

private fun TrainerKind.label() =
    when (this) {
        TrainerKind.ITEM -> R.string.trainer_item
        TrainerKind.SUPPORTER -> R.string.trainer_supporter
        TrainerKind.TOOL -> R.string.trainer_tool
        TrainerKind.STADIUM -> R.string.trainer_stadium
    }

private fun Rarity.label() =
    when (this) {
        Rarity.COMMON -> R.string.rarity_common
        Rarity.UNCOMMON -> R.string.rarity_uncommon
        Rarity.RARE -> R.string.rarity_rare
        Rarity.DOUBLE_RARE -> R.string.rarity_double_rare
        Rarity.ART_RARE -> R.string.rarity_art_rare
        Rarity.SUPER_RARE -> R.string.rarity_super_rare
        Rarity.SHINY -> R.string.rarity_shiny
        Rarity.SPECIAL_ART_RARE -> R.string.rarity_special_art_rare
        Rarity.SHINY_SUPER_RARE -> R.string.rarity_shiny_super_rare
        Rarity.IMMERSIVE -> R.string.rarity_immersive
        Rarity.CROWN -> R.string.rarity_crown
    }
