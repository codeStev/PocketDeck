package dev.codestev.pocketdesign.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.brand.SignalBrand
import dev.codestev.pocketdesign.components.PdAction
import dev.codestev.pocketdesign.components.PdArtTile
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCardFacePlaceholder
import dev.codestev.pocketdesign.components.PdCardFanPlaceholder
import dev.codestev.pocketdesign.components.PdChevron
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdEmptyState
import dev.codestev.pocketdesign.components.PdFilterChip
import dev.codestev.pocketdesign.components.PdFloatingNav
import dev.codestev.pocketdesign.components.PdFullScreenPanel
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdIconButtonStyle
import dev.codestev.pocketdesign.components.PdInfoCard
import dev.codestev.pocketdesign.components.PdMeterCell
import dev.codestev.pocketdesign.components.PdMeterRow
import dev.codestev.pocketdesign.components.PdMeterTable
import dev.codestev.pocketdesign.components.PdNavTab
import dev.codestev.pocketdesign.components.PdProgressMeter
import dev.codestev.pocketdesign.components.PdRowGroup
import dev.codestev.pocketdesign.components.PdSegmentedTabs
import dev.codestev.pocketdesign.components.PdSpinner
import dev.codestev.pocketdesign.components.PdStepper
import dev.codestev.pocketdesign.components.PdSwitch
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.components.PdTextLink
import dev.codestev.pocketdesign.components.PdTintedCard
import dev.codestev.pocketdesign.components.PdTopBar
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val NoOp: () -> Unit = {}

@Composable
internal fun ActionSections() {
    CatalogSection("Buttons") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        ) {
            PdButton("Import code", NoOp)
            PdButton("Save", NoOp, style = PdButtonStyle.Secondary)
            PdButton("Opening odds", NoOp, style = PdButtonStyle.Tonal)
            PdButton("See all", NoOp, style = PdButtonStyle.Text)
            PdButton("Delete", NoOp, style = PdButtonStyle.Danger)
            PdButton("Small", NoOp, size = PdButtonSize.Small)
            PdButton("Disabled", NoOp, enabled = false)
            PdButton("Saving", NoOp, loading = true)
            PdButton("Scan", NoOp, leadingIcon = PdIcons.Scan)
        }
        PdButton("Build a deck from my cards", NoOp, modifier = Modifier.fillMaxWidth())
    }
    CatalogSection("Icon buttons") {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            PdIconButton(PdIcons.Bell, "Notifications", NoOp, badge = true)
            PdIconButton(PdIcons.Plus, "Add", NoOp, style = PdIconButtonStyle.Accent)
            PdIconButton(PdIcons.Search, "Search", NoOp, style = PdIconButtonStyle.Plain)
        }
    }
    CatalogSection("Stepper") {
        var count by rememberSaveable { mutableIntStateOf(2) }
        PdStepper(count, { count = it }, 0..4, "Remove one copy", "Add one copy")
    }
    CatalogSection("Chips") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        ) {
            PdChip("Default")
            FilterChips()
            PdChip("Selected", kind = PdChipKind.Selected, leadingIcon = PdIcons.Check)
            PdChip("You can build this", kind = PdChipKind.Success, leadingIcon = PdIcons.Check)
            PdChip("Needs stand-ins", kind = PdChipKind.Warning)
            PdChip("2 cards missing", kind = PdChipKind.Danger)
            PdChip("Blue", dot = PocketDesign.colors.tint(SignalBrand.BLUE).strong)
            PdChip("Orange", dot = PocketDesign.colors.tint(SignalBrand.ORANGE).strong)
        }
    }
}

@Composable
internal fun NavigationSections() {
    val colors = PocketDesign.colors
    CatalogSection("Top bar") {
        PdTopBar(tag = "Main", tagTrailingIcon = PdIcons.ChevronDown, onTagClick = NoOp) {
            PdIconButton(PdIcons.Refresh, "Refresh", NoOp, style = PdIconButtonStyle.Plain)
        }
    }
    CatalogSection("Floating navigation") {
        var selected by rememberSaveable { mutableIntStateOf(1) }
        val tabs =
            listOf(
                PdNavTab("Collection", PdIcons.Collection, colors.accent),
                PdNavTab("Decks", PdIcons.Decks, colors.tint(SignalBrand.BLUE).strong),
                PdNavTab("Scan", PdIcons.Scan, colors.tint(SignalBrand.GREEN).strong),
                PdNavTab("Settings", PdIcons.Settings, colors.tint(SignalBrand.STEEL).strong),
            )
        PdFloatingNav(tabs, selected, { selected = it })
    }
    CatalogSection("Segmented tabs") {
        var three by rememberSaveable { mutableIntStateOf(0) }
        var two by rememberSaveable { mutableIntStateOf(1) }
        PdSegmentedTabs(listOf("Tournament", "Generated", "Saved"), three, { three = it }, Modifier.fillMaxWidth())
        PdSegmentedTabs(listOf("Going first", "Going second"), two, { two = it }, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun SurfaceSections() {
    val colors = PocketDesign.colors
    CatalogSection("Tinted card") {
        PdTintedCard(colors.tint(SignalBrand.BLUE), art = { DeckArt(SignalBrand.BLUE) }) {
            Text("Mewtwo ex · Gardevoir", style = PocketDesign.typography.titleMd, color = colors.ink)
            PdChip("1 card missing", kind = PdChipKind.Danger)
            PdProgressMeter(0.95f, label = "Owned", valueText = "95%")
            PdButton("Import code", NoOp, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
                PdButton(
                    "Opening odds",
                    NoOp,
                    Modifier.weight(1f),
                    style = PdButtonStyle.Secondary,
                    size = PdButtonSize.Small,
                )
                PdButton(
                    "Saved",
                    NoOp,
                    Modifier.weight(1f),
                    style = PdButtonStyle.Secondary,
                    size = PdButtonSize.Small,
                    enabled = false,
                )
            }
        }
    }
    CatalogSection("Art tiles") {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
            ArtTile(SignalBrand.ORANGE, copies = 3, owned = true, modifier = Modifier.weight(1f))
            ArtTile(SignalBrand.BLUE, copies = 1, owned = true, modifier = Modifier.weight(1f))
            ArtTile(SignalBrand.GREEN, copies = 0, owned = false, modifier = Modifier.weight(1f))
        }
    }
    CatalogSection("Dialogs") {
        var showDialog by rememberSaveable { mutableStateOf(false) }
        var showPanel by rememberSaveable { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            PdButton("Open dialog", { showDialog = true }, style = PdButtonStyle.Secondary)
            PdButton("Open panel", { showPanel = true }, style = PdButtonStyle.Secondary)
        }
        if (showDialog) {
            PdDialog(
                "Delete this deck?",
                { showDialog = false },
                "Delete",
                { showDialog = false },
                dismissText = "Cancel",
                destructive = true,
            ) {
                Text(
                    "It is removed from this account. You can build it again from the tournament list.",
                    style = PocketDesign.typography.body,
                    color = colors.ink,
                )
            }
        }
        if (showPanel) {
            PdFullScreenPanel("What to chase next", "Close", { showPanel = false }) {
                Text(
                    "A full-screen panel for a task of its own.",
                    Modifier.padding(PdSpacing.space4),
                    style = PocketDesign.typography.body,
                    color = colors.ink,
                )
            }
        }
    }
    CatalogSection("Card") {
        PdInfoCard(
            "Friend code",
            "AB-1234-5678",
            actionIcon = PdIcons.Copy,
            actionDescription = "Copy",
            onAction = NoOp,
        )
    }
}

@Composable
private fun FilterChips() {
    var on by remember { mutableStateOf(true) }
    var off by remember { mutableStateOf(false) }
    PdFilterChip("Owned only", on, { on = it })
    PdFilterChip("Rare", off, { off = it })
}

@Composable
private fun BoxScope.DeckArt(tintKey: String) {
    PdCardFanPlaceholder(PocketDesign.colors.tint(tintKey))
}

@Composable
private fun ArtTile(
    tintKey: String,
    copies: Int,
    owned: Boolean,
    modifier: Modifier,
) {
    val tint = PocketDesign.colors.tint(tintKey)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        PdArtTile(tint, "Card, owned $copies", copies = copies, owned = owned) { PdCardFacePlaceholder(tint) }
        Text(
            if (owned) "Owned $copies" else "Not owned",
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
    }
}

@Composable
internal fun FeedbackSections() {
    CatalogSection("Banners") {
        Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
            PdBanner("Opening odds are simulated from the card texts the app can read.")
            PdBanner("You can build this deck with the cards you own.", kind = PdBannerKind.Success)
            PdBanner("This deck needs two stand-ins for cards you do not own.", kind = PdBannerKind.Warning)
            PdBanner(
                "Could not load tournament decks. Check your connection.",
                kind = PdBannerKind.Danger,
                actionLabel = "Try again",
            )
        }
    }
    CatalogSection("Empty state") {
        PdEmptyState(
            title = "No saved decks yet",
            message = "Tap Save on a deck and it is kept here, for this account.",
            modifier = Modifier.fillMaxWidth(),
            primaryAction = PdAction("Browse tournament decks", NoOp),
            secondaryAction = PdAction("How saving works", NoOp),
        )
    }
    CatalogSection("Progress and meter table") {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            PdSpinner(size = 20.dp, strokeWidth = 2.dp)
            PdSpinner()
            PdSpinner(size = 40.dp)
        }
        PdProgressMeter(0.95f, label = "Owned", valueText = "95%")
        PdMeterTable(
            headers = listOf("1", "2", "3", "4"),
            rows =
                listOf(
                    row("Mewtwo ex", 0f, 0f, 0.76f, 0.92f),
                    row("Gardevoir", 0f, 0f, 0.17f, 0.47f),
                    row("Kirlia", 0f, 0f, 0.13f, 0.30f),
                ),
        )
    }
}

private fun row(
    name: String,
    vararg shares: Float,
) = PdMeterRow(name, shares.map { PdMeterCell("${(it * 100).toInt()}%", it, emphasized = it >= 0.5f) })

@Composable
internal fun InputSections() {
    CatalogSection("Text fields") {
        var query by rememberSaveable { mutableStateOf("") }
        var model by rememberSaveable { mutableStateOf("nvidia/nemotron-3-super") }
        var secret by rememberSaveable { mutableStateOf("sk-1234") }
        Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            PdTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                placeholder = "Search cards",
                leadingIcon = PdIcons.Search,
            )
            PdTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                supportingText = "Empty = default. Several, separated by commas, are tried in order.",
            )
            PdTextField(
                value = secret,
                onValueChange = { secret = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "API key",
                visualTransformation = PasswordVisualTransformation(),
            )
            PdTextField(
                "abc",
                {},
                Modifier.fillMaxWidth(),
                isError = true,
                supportingText = "That key was not accepted.",
            )
        }
    }
    CatalogSection("Switch") {
        var auto by rememberSaveable { mutableStateOf(true) }
        var replace by rememberSaveable { mutableStateOf(false) }
        PdSwitch(auto, { auto = it }, "Scroll automatically", supportingText = "The app scrolls the list for you.")
        PdSwitch(replace, { replace = it }, "Replace my collection")
        PdSwitch(true, {}, "Disabled", enabled = false)
    }
    CatalogSection("Rows") {
        PdRowGroup {
            row("Online status", NoOp, value = "All friends", trailing = { PdTextLink("Change") })
            row("Game activity", NoOp, value = "All users", trailing = { PdTextLink("Change") })
            row("Language", NoOp, trailing = { PdChevron() })
        }
    }
}
