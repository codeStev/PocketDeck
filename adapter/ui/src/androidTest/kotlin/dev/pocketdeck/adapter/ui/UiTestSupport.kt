package dev.pocketdeck.adapter.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocketdeck.adapter.ui.collection.CardDetail
import dev.pocketdeck.adapter.ui.collection.CardItem
import dev.pocketdeck.adapter.ui.decks.DeckCardLine
import dev.pocketdeck.adapter.ui.decks.DeckItem
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.deck.SuggestionOrigin
import io.kotest.matchers.shouldBe

/** The text of a string resource in the language of the phone under test. */
internal fun string(
    id: Int,
    vararg args: Any,
): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

internal fun cardItem(
    id: String,
    name: String,
    copies: Int,
) = CardItem(
    CardId(id),
    name,
    CardDetail.OfPokemon(EnergyType.GRASS, Stage.BASIC, 60),
    artworkUrl = null,
    copies = copies,
)

internal fun deckItem(title: String = "Mewtwo ex Gardevoir") =
    DeckItem(
        key = "$title#0",
        title = title,
        origin = SuggestionOrigin.META,
        ownedPercent = 85,
        missingCopies = 3,
        totalCopies = 20,
        cards = listOf(DeckCardLine("Mewtwo ex", artworkUrl = null, copies = 2, missingCopies = 1, isPokemon = true)),
        energyTypes = listOf(EnergyType.PSYCHIC),
    )

/**
 * Every control that can be tapped must say what it does: a text, or a description for an icon. This is what a screen reader
 * reads, so a missing label would make a screen unusable for it.
 */
internal fun ComposeContentTestRule.assertEveryControlIsLabelled() {
    val unlabelled =
        onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .filter { node ->
                // An empty description counts as none: it is read as nothing.
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .none { it.text.isNotBlank() } &&
                    node.config
                        .getOrNull(SemanticsProperties.ContentDescription)
                        .orEmpty()
                        .none { it.isNotBlank() }
            }
    unlabelled.map { it.boundsInRoot } shouldBe emptyList()
}
