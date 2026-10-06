package dev.pocketdeck.adapter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.pocketdeck.adapter.ui.collection.CollectionScreen
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.adapter.ui.decks.DecksScreen
import dev.pocketdeck.adapter.ui.decks.DecksViewModel
import dev.pocketdeck.adapter.ui.scan.ScanScreen
import dev.pocketdeck.adapter.ui.scan.ScanViewModel

enum class AppTab { COLLECTION, DECKS, SCAN }

/** The app's top-level layout: three tabs. Everything platform specific (permissions, services) comes in as callbacks. */
@Composable
fun PocketDeckApp(
    collectionViewModel: CollectionViewModel,
    decksViewModel: DecksViewModel,
    scanViewModel: ScanViewModel,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    autoScrollEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (automatic: Boolean) -> Unit,
) {
    Scaffold(
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
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.COLLECTION -> CollectionScreen(collectionViewModel)
                AppTab.DECKS -> DecksScreen(decksViewModel)
                AppTab.SCAN -> ScanScreen(scanViewModel, autoScrollEnabled, onOpenAccessibilitySettings, onStartScan)
            }
        }
    }
}
