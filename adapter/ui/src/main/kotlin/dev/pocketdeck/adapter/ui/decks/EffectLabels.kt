package dev.pocketdeck.adapter.ui.decks

import androidx.annotation.StringRes
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.domain.card.EffectTag

@StringRes
internal fun EffectTag.label(): Int =
    when (this) {
        EffectTag.DISCARD_OWN_ENERGY -> R.string.effect_discard_own_energy
        EffectTag.DISCARD_OPPONENT_ENERGY -> R.string.effect_discard_opponent_energy
        EffectTag.ATTACH_ENERGY -> R.string.effect_attach_energy
        EffectTag.ATTACH_ENERGY_FROM_ZONE -> R.string.effect_attach_energy_from_zone
        EffectTag.ATTACH_ENERGY_FROM_DISCARD -> R.string.effect_attach_energy_from_discard
        EffectTag.DRAW -> R.string.effect_draw
        EffectTag.SEARCH_DECK -> R.string.effect_search_deck
        EffectTag.HEAL -> R.string.effect_heal
        EffectTag.SWITCH -> R.string.effect_switch
        EffectTag.BENCH_DAMAGE -> R.string.effect_bench_damage
        EffectTag.RANDOM_TARGETS -> R.string.effect_random_targets
        EffectTag.DAMAGE_SCALING -> R.string.effect_damage_scaling
        EffectTag.DAMAGE_REDUCTION -> R.string.effect_damage_reduction
        EffectTag.SELF_DAMAGE -> R.string.effect_self_damage
        EffectTag.COIN_FLIP -> R.string.effect_coin_flip
        EffectTag.POISON -> R.string.effect_poison
        EffectTag.BURN -> R.string.effect_burn
        EffectTag.SLEEP -> R.string.effect_sleep
        EffectTag.PARALYZE -> R.string.effect_paralyze
        EffectTag.CONFUSE -> R.string.effect_confuse
        EffectTag.PREVENT_ACTION -> R.string.effect_prevent_action
        EffectTag.MOVE_ENERGY -> R.string.effect_move_energy
        EffectTag.RETREAT_COST -> R.string.effect_retreat_cost
        EffectTag.SNIPE -> R.string.effect_snipe
        EffectTag.HAND_DISRUPTION -> R.string.effect_hand_disruption
        EffectTag.TOOL_REMOVAL -> R.string.effect_tool_removal
        EffectTag.RECOVER_FROM_DISCARD -> R.string.effect_recover_from_discard
        EffectTag.COUNTER_DAMAGE -> R.string.effect_counter_damage
    }
