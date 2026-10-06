package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.application.advice.AskAiForDeck
import dev.pocketdeck.application.advice.ObserveAdvisorSettings

/** The inbound ports behind the AI part of the decks screen; the settings themselves are edited on the settings screen. */
class AiAssistant(
    val ask: AskAiForDeck,
    val observeSettings: ObserveAdvisorSettings,
)
