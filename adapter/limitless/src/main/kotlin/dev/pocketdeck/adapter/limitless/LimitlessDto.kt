package dev.pocketdeck.adapter.limitless

import kotlinx.serialization.Serializable

@Serializable
internal data class TournamentDto(
    val id: String,
    val players: Int = 0,
)

@Serializable
internal data class StandingDto(
    val decklist: DecklistDto? = null,
    val deck: DeckDto? = null,
    val placing: Int? = null,
    val record: RecordDto? = null,
)

@Serializable
internal data class DeckDto(
    val name: String,
)

@Serializable
internal data class RecordDto(
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
)

@Serializable
internal data class DecklistDto(
    val pokemon: List<CardLineDto> = emptyList(),
    val trainer: List<CardLineDto> = emptyList(),
    val energy: List<String> = emptyList(),
)

@Serializable
internal data class CardLineDto(
    val count: Int,
    val set: String,
    val number: String,
)
