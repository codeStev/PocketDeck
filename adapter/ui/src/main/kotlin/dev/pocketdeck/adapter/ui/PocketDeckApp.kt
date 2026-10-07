package dev.pocketdeck.adapter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.COLLECTION,
                    onClick = { onTabSelected(AppTab.COLLECTION) },
                    icon = {},
                    label = { Text(stringResource(R.string.tab_collection)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.DECKS,
                    onClick = { onTabSelected(AppTab.DECKS) },
                    icon = {},
                    label = { Text(stringResource(R.string.tab_decks)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SCAN,
                    onClick = { onTabSelected(AppTab.SCAN) },
                    icon = {},
                    label = { Text(stringResource(R.string.tab_scan)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { onTabSelected(AppTab.SETTINGS) },
                    icon = {},
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.COLLECTION -> {
                    CollectionScreen(collectionViewModel)
                }

                AppTab.DECKS -> {
                    DecksScreen(decksViewModel) { onTabSelected(AppTab.SETTINGS) }
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
