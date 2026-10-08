package dev.pocketdeck.adapter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codestev.pocketdesign.components.PdFloatingNav
import dev.codestev.pocketdesign.components.PdNavTab
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.chase.ChaseViewModel
import dev.pocketdeck.adapter.ui.collection.CollectionScreen
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.adapter.ui.decks.DecksScreen
import dev.pocketdeck.adapter.ui.decks.DecksViewModel
import dev.pocketdeck.adapter.ui.profile.ProfileSwitcher
import dev.pocketdeck.adapter.ui.profile.ProfilesViewModel
import dev.pocketdeck.adapter.ui.scan.ScanScreen
import dev.pocketdeck.adapter.ui.scan.ScanViewModel
import dev.pocketdeck.adapter.ui.settings.SettingsScreen
import dev.pocketdeck.adapter.ui.settings.SettingsViewModel

enum class AppTab { COLLECTION, DECKS, SCAN, SETTINGS }

/** The app's top-level layout: four tabs. Everything platform specific (permissions, services) comes in as callbacks. */
@Composable
fun PocketDeckApp(
    collectionViewModel: CollectionViewModel,
    decksViewModel: DecksViewModel,
    chaseViewModel: ChaseViewModel,
    scanViewModel: ScanViewModel,
    settingsViewModel: SettingsViewModel,
    profilesViewModel: ProfilesViewModel,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    autoScrollEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (automatic: Boolean) -> Unit,
) {
    Scaffold(
        topBar = { ProfileSwitcher(profilesViewModel) },
        bottomBar = { FloatingTabs(selectedTab, onTabSelected) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.COLLECTION -> {
                    CollectionScreen(collectionViewModel)
                }

                AppTab.DECKS -> {
                    DecksScreen(decksViewModel, chaseViewModel) { onTabSelected(AppTab.SETTINGS) }
                }

                AppTab.SCAN -> {
                    ScanTab(
                        scanViewModel,
                        profilesViewModel,
                        autoScrollEnabled,
                        onOpenAccessibilitySettings,
                        onStartScan,
                    )
                }

                AppTab.SETTINGS -> {
                    SettingsScreen(settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun ScanTab(
    scanViewModel: ScanViewModel,
    profilesViewModel: ProfilesViewModel,
    autoScrollEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (automatic: Boolean) -> Unit,
) {
    val profiles by profilesViewModel.state.collectAsStateWithLifecycle()
    val accountName =
        profiles.profiles
            ?.active
            ?.name
            ?.value
    ScanScreen(scanViewModel, accountName, autoScrollEnabled, onOpenAccessibilitySettings, onStartScan)
}

/** Each tab owns a colour from the brand, so the player learns "green is scan" without reading. */
@Composable
private fun FloatingTabs(
    selected: AppTab,
    onSelected: (AppTab) -> Unit,
) {
    val colors = PocketDesign.colors
    val tabs =
        listOf(
            PdNavTab(stringResource(R.string.tab_collection), PdIcons.Collection, colors.accent),
            PdNavTab(stringResource(R.string.tab_decks), PdIcons.Decks, colors.tint("blue").strong),
            PdNavTab(stringResource(R.string.tab_scan), PdIcons.Scan, colors.tint("green").strong),
            PdNavTab(stringResource(R.string.tab_settings), PdIcons.Settings, colors.tint("steel").strong),
        )
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        PdFloatingNav(tabs, AppTab.entries.indexOf(selected), { onSelected(AppTab.entries[it]) })
    }
}
