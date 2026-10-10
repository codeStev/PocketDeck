package dev.pocketdeck.adapter.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocketdeck.adapter.ui.collection.CardType
import dev.pocketdeck.adapter.ui.collection.CollectionContent
import dev.pocketdeck.adapter.ui.collection.CollectionFilter
import dev.pocketdeck.adapter.ui.collection.CollectionSort
import dev.pocketdeck.adapter.ui.collection.CollectionUiState
import dev.pocketdeck.adapter.ui.collection.FilterOptions
import dev.pocketdeck.adapter.ui.decks.DecksActions
import dev.pocketdeck.adapter.ui.decks.DecksContent
import dev.pocketdeck.adapter.ui.decks.DecksNotice
import dev.pocketdeck.adapter.ui.decks.DecksUiState
import dev.pocketdeck.adapter.ui.files.CollectionFilesViewModel
import dev.pocketdeck.adapter.ui.scan.ScanContent
import dev.pocketdeck.adapter.ui.scan.ScanUiState
import dev.pocketdeck.adapter.ui.settings.AboutActions
import dev.pocketdeck.adapter.ui.settings.SettingsActions
import dev.pocketdeck.adapter.ui.settings.SettingsContent
import dev.pocketdeck.adapter.ui.settings.SettingsUiState
import dev.pocketdeck.adapter.ui.theme.PocketDeckTheme
import dev.pocketdeck.adapter.ui.welcome.WelcomeFlow
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.exchange.ExportedFile
import dev.pocketdeck.application.exchange.FileFormat
import dev.pocketdeck.application.exchange.ImportFailure
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Rarity
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test

/** Starts the screens with made-up data and checks that the key controls exist, work and are labelled. */
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun decksActions(
        onShowCode: (String) -> Unit = {},
        onShare: (String, Boolean) -> Unit = { _, _ -> },
    ) = DecksActions(
        onRefresh = {},
        onShowCode = onShowCode,
        onCloseCode = {},
        onOpenBuilder = {},
        onCloseBuilder = {},
        onBuild = {},
        onAskAi = {},
        onOpenSettings = {},
        onOpenChase = {},
        onShowOpening = {},
        onSave = {},
        onRenameSaved = {},
        onDeleteSaved = {},
        onRenamed = {},
        onDeleteConfirmed = {},
        onSavedDialogDismissed = {},
        onShare = onShare,
        onShareHandled = {},
    )

    private fun collection(
        state: CollectionUiState,
        onFilterChange: (CollectionFilter) -> Unit = {},
        onOpenScan: () -> Unit = {},
    ) = compose.setContent {
        PocketDeckTheme {
            CollectionContent(state, "", {}, onFilterChange, { _, _ -> }, {}, onOpenScan)
        }
    }

    private val emptyCollection =
        CollectionUiState(
            items = listOf(cardItem("a1-001", "Bulbasaur", 0), cardItem("a1-002", "Ivysaur", 0)),
            catalogSize = 2,
        )

    @Test
    fun collectionHintsAtScanningWhenItIsEmpty() {
        var opened = false
        collection(emptyCollection, onOpenScan = { opened = true })

        compose.onNodeWithText(string(R.string.collection_empty_hint)).assertExists()
        compose.onNodeWithText(string(R.string.collection_empty_action)).performClick()

        opened shouldBe true
    }

    @Test
    fun collectionOwnedOnlyChipSwitchesTheFilter() {
        var ownedOnly: Boolean? = null
        collection(emptyCollection, onFilterChange = { ownedOnly = it.ownedOnly })

        compose.onNodeWithText(string(R.string.collection_owned_only)).performClick()

        ownedOnly shouldBe true
    }

    @Test
    fun collectionFilterPanelOffersSortRarityAndTypeAndReportsTheChoice() {
        // The state here is fixed, so every tap reports what that one tap changes.
        val reported = mutableListOf<CollectionFilter>()
        collection(
            emptyCollection.copy(
                options =
                    FilterOptions(
                        sets = listOf("a1"),
                        rarities = listOf(Rarity.COMMON),
                        types = listOf(CardType.OfEnergy(EnergyType.GRASS)),
                    ),
            ),
            onFilterChange = { reported += it },
        )

        compose.onNodeWithText(string(R.string.collection_filters)).performClick()
        // "Rarity" is both a sort choice and the title of the rarity group.
        compose.onAllNodesWithText(string(R.string.collection_filter_rarity)).assertCountEquals(2)
        compose.onNodeWithText(string(R.string.rarity_common)).performClick()
        compose.onNodeWithText(string(R.string.collection_sort_name)).performClick()
        compose.onNodeWithText(string(R.string.collection_order_descending)).performClick()

        reported.map { Triple(it.rarities, it.sort, it.descending) } shouldBe
            listOf(
                Triple(setOf(Rarity.COMMON), CollectionSort.NUMBER, false),
                Triple(emptySet<Rarity>(), CollectionSort.NAME, false),
                Triple(emptySet<Rarity>(), CollectionSort.NUMBER, true),
            )
    }

    @Test
    fun collectionTileOpensACardDialogWithAStepper() {
        collection(
            CollectionUiState(
                items = listOf(cardItem("a1-001", "Bulbasaur", 2)),
                uniqueOwned = 1,
                totalOwned = 2,
                catalogSize = 1,
            ),
        )

        compose.onNodeWithContentDescription(tileDescription("Bulbasaur", 2)).performClick()

        compose.onNodeWithContentDescription(string(R.string.collection_add_copy, "Bulbasaur")).assertExists()
        compose.onNodeWithContentDescription(string(R.string.collection_remove_copy, "Bulbasaur")).assertExists()
    }

    private fun tileDescription(
        name: String,
        copies: Int,
    ) = InstrumentationRegistry
        .getInstrumentation()
        .targetContext.resources
        .getQuantityString(R.plurals.collection_tile_owned, copies, name, copies)

    @Test
    fun collectionControlsAreLabelled() {
        collection(emptyCollection)

        compose.assertEveryControlIsLabelled()
    }

    @Test
    fun deckCardOffersImportAndSharing() {
        var code: String? = null
        var shared: Pair<String, Boolean>? = null
        val actions = decksActions(onShowCode = { code = it }, onShare = { key, image -> shared = key to image })
        compose.setContent {
            PocketDeckTheme { DecksContent(DecksUiState(items = listOf(deckItem()), loaded = true), actions) }
        }

        compose.onNodeWithText(string(R.string.decks_code_show)).performClick()
        code shouldBe "Mewtwo ex Gardevoir#0"

        compose.onNodeWithContentDescription(string(R.string.share_deck)).performClick()
        compose.onNodeWithText(string(R.string.share_as_text)).assertExists()
        compose.onNodeWithText(string(R.string.share_as_image)).performClick()
        shared shouldBe ("Mewtwo ex Gardevoir#0" to true)
    }

    @Test
    fun decksSayWhenTheTournamentSiteIsRateLimiting() {
        compose.setContent {
            PocketDeckTheme {
                DecksContent(DecksUiState(loaded = true, notice = DecksNotice.RATE_LIMITED), decksActions())
            }
        }

        compose.onNodeWithText(string(R.string.decks_rate_limited)).assertExists()
    }

    @Test
    fun decksControlsAreLabelled() {
        compose.setContent {
            PocketDeckTheme { DecksContent(DecksUiState(items = listOf(deckItem()), loaded = true), decksActions()) }
        }

        compose.assertEveryControlIsLabelled()
    }

    private fun scan(
        state: ScanUiState,
        serviceOn: Boolean,
        onStart: (Boolean) -> Unit = {},
        gameInstalled: Boolean = true,
    ) = compose.setContent {
        PocketDeckTheme {
            ScanContent(
                state,
                "Main",
                serviceOn,
                gameInstalled,
                {},
                {},
                onStart,
                {},
                { _, _ -> },
                {},
                {},
                {},
                {},
                {},
                {},
                {},
            )
        }
    }

    @Test
    fun scanSaysTheGameIsNotInstalledAndKeepsStartDisabled() {
        scan(ScanUiState(), serviceOn = true, gameInstalled = false)

        compose.onNodeWithText(string(R.string.scan_game_missing)).assertExists()
        compose.onNodeWithText(string(R.string.scan_start)).assertIsNotEnabled()
    }

    @Test
    fun scanSaysAutoScrollNeedsItsServiceAndKeepsStartDisabled() {
        scan(ScanUiState(automatic = true, autoScrollConsented = true), serviceOn = false)

        compose.onNodeWithText(string(R.string.scan_automatic_needs_service)).assertExists()
        compose.onNodeWithText(string(R.string.scan_open_accessibility)).assertExists()
        compose.onNodeWithText(string(R.string.scan_start)).assertIsNotEnabled()
    }

    @Test
    fun scanStartsWhenTheServiceIsOn() {
        var automatic: Boolean? = null
        scan(ScanUiState(automatic = true, autoScrollConsented = true), serviceOn = true, onStart = { automatic = it })

        compose.onNodeWithText(string(R.string.scan_start)).assertIsEnabled().performClick()

        automatic shouldBe true
    }

    @Test
    fun scanControlsAreLabelled() {
        scan(ScanUiState(automatic = false), serviceOn = false)

        compose.assertEveryControlIsLabelled()
    }

    @Test
    fun theWelcomeTourEndsWithAnExplicitAgreementAfterSkipping() {
        var finished = false
        compose.setContent { PocketDeckTheme { WelcomeFlow(firstRun = true) { finished = true } } }

        compose.onNodeWithText(string(R.string.welcome_title_1)).assertExists()
        compose.onNodeWithText(string(R.string.welcome_skip)).performClick()
        compose.onNodeWithText(string(R.string.welcome_title_3)).assertExists()
        finished shouldBe false

        compose.onNodeWithText(string(R.string.welcome_agree)).performClick()
        finished shouldBe true
    }

    @Test
    fun theReopenedTourEndsWithDone() {
        compose.setContent { PocketDeckTheme { WelcomeFlow(firstRun = false) {} } }

        compose.onNodeWithText(string(R.string.welcome_next)).performClick()
        compose.onNodeWithText(string(R.string.welcome_next)).performClick()

        compose.onNodeWithText(string(R.string.welcome_done)).assertExists()
    }

    private fun settings() =
        compose.setContent {
            val files =
                remember {
                    CollectionFilesViewModel({
                        ExportedFile("a.json", FileFormat.JSON, "")
                    }, { Outcome.Failure(ImportFailure.NotACollectionFile) }, {})
                }
            val actions =
                SettingsActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, {})
            PocketDeckTheme {
                SettingsContent(
                    SettingsUiState(),
                    files,
                    AboutActions("1.0.0", { "" }, {}, {}),
                    actions,
                )
            }
        }

    @Test
    fun settingsOfferTheCollectionFilesAndAbout() {
        settings()

        compose.onNodeWithText(string(R.string.files_export_json)).assertExists()
        compose.onNodeWithText(string(R.string.files_export_csv)).assertExists()
        compose.onNodeWithText(string(R.string.files_import)).assertExists()
        compose.onNodeWithText(string(R.string.settings_about)).assertExists()
    }

    @Test
    fun settingsOfferDiagnosticsAndReportingAProblemWithoutOpeningAbout() {
        settings()

        compose.onNodeWithText(string(R.string.settings_support_title)).assertExists()
        compose.onNodeWithText(string(R.string.settings_support_copy)).assertExists()
        compose.onNodeWithText(string(R.string.settings_support_report)).assertExists()
    }

    @Test
    fun settingsControlsAreLabelled() {
        settings()

        compose.assertEveryControlIsLabelled()
    }
}
