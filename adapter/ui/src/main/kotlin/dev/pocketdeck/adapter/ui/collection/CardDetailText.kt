package dev.pocketdeck.adapter.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerKind

@Composable
internal fun detailText(detail: CardDetail): String =
    when (detail) {
        is CardDetail.OfPokemon -> {
            stringResource(
                R.string.collection_detail_pokemon,
                stringResource(detail.energy.label()),
                stringResource(detail.stage.label()),
                detail.hp,
            )
        }

        is CardDetail.OfTrainer -> {
            stringResource(detail.kind.label())
        }
    }

private fun EnergyType.label() =
    when (this) {
        EnergyType.GRASS -> R.string.energy_grass
        EnergyType.FIRE -> R.string.energy_fire
        EnergyType.WATER -> R.string.energy_water
        EnergyType.LIGHTNING -> R.string.energy_lightning
        EnergyType.PSYCHIC -> R.string.energy_psychic
        EnergyType.FIGHTING -> R.string.energy_fighting
        EnergyType.DARKNESS -> R.string.energy_darkness
        EnergyType.METAL -> R.string.energy_metal
        EnergyType.DRAGON -> R.string.energy_dragon
        EnergyType.COLORLESS -> R.string.energy_colorless
    }

private fun Stage.label() =
    when (this) {
        Stage.BASIC -> R.string.stage_basic
        Stage.STAGE_1 -> R.string.stage_1
        Stage.STAGE_2 -> R.string.stage_2
    }

private fun TrainerKind.label() =
    when (this) {
        TrainerKind.ITEM -> R.string.trainer_item
        TrainerKind.SUPPORTER -> R.string.trainer_supporter
        TrainerKind.TOOL -> R.string.trainer_tool
        TrainerKind.STADIUM -> R.string.trainer_stadium
    }
