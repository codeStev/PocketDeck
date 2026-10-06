package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.application.advice.AskAiForDeck
import dev.pocketdeck.application.advice.ObserveAdvisorSettings
import dev.pocketdeck.application.advice.SaveAdvisorSettings

/** The inbound ports behind the AI part of the decks screen. */
class AiAssistant(
    val ask: AskAiForDeck,
    val observeSettings: ObserveAdvisorSettings,
    val saveSettings: SaveAdvisorSettings,
)
