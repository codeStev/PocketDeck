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
import dev.pocketdeck.adapter.ui.scan.ScanScreen
import dev.pocketdeck.adapter.ui.scan.ScanViewModel

enum class AppTab { COLLECTION, SCAN }

/** The app's top-level layout: two tabs. Everything platform specific (permissions, services) comes in as callbacks. */
@Composable
fun PocketDeckApp(
    collectionViewModel: CollectionViewModel,
    scanViewModel: ScanViewModel,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    onStartScan: () -> Unit,
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
                AppTab.SCAN -> ScanScreen(scanViewModel, onStartScan)
            }
        }
    }
}
