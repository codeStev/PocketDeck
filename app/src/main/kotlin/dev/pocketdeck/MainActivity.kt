package dev.pocketdeck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.pocketdeck.adapter.capture.AutoScroll
import dev.pocketdeck.adapter.capture.OverlayPermission
import dev.pocketdeck.adapter.capture.RequestScreenCapture
import dev.pocketdeck.adapter.capture.ScanCapture
import dev.pocketdeck.adapter.ui.AppTab
import dev.pocketdeck.adapter.ui.PocketDeckApp
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.adapter.ui.scan.ScanViewModel
import dev.pocketdeck.adapter.ui.theme.PocketDeckTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var collectionViewModelFactory: CollectionViewModel.Factory

    @Inject
    lateinit var scanViewModelFactory: ScanViewModel.Factory

    private var selectedTab by mutableStateOf(AppTab.COLLECTION)
    private var reviewRequests by mutableIntStateOf(0)
    private var autoScrollEnabled by mutableStateOf(false)
    private var pendingAutomatic = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            PocketDeckTheme {
                val collectionViewModel = viewModel<CollectionViewModel>(factory = collectionViewModelFactory)
                val scanViewModel = viewModel<ScanViewModel>(factory = scanViewModelFactory)
                LaunchedEffect(reviewRequests) { if (reviewRequests > 0) scanViewModel.onReviewRequested() }

                val captureConsent =
                    rememberLauncherForActivityResult(RequestScreenCapture()) { consent ->
                        if (consent != null) {
                            ScanCapture.start(this@MainActivity, consent, pendingAutomatic)
                            openGame()
                        }
                    }
                val notificationPermission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        // The scan works without the notification being visible, so a refusal does not stop it.
                        captureConsent.launch(Unit)
                    }
                val overlaySettings =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

                PocketDeckApp(
                    collectionViewModel = collectionViewModel,
                    scanViewModel = scanViewModel,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    autoScrollEnabled = autoScrollEnabled,
                    onOpenAccessibilitySettings = { startActivity(AutoScroll.settingsIntent()) },
                    onStartScan = { automatic ->
                        pendingAutomatic = automatic
                        when {
                            !OverlayPermission.isGranted(this@MainActivity) -> {
                                overlaySettings.launch(OverlayPermission.settingsIntent(this@MainActivity))
                            }

                            needsNotificationPermission() -> {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }

                            else -> {
                                captureConsent.launch(Unit)
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The player may just have switched the accessibility service on or off in the system settings.
        autoScrollEnabled = AutoScroll.isEnabled(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ScanCapture.ACTION_REVIEW) {
            selectedTab = AppTab.SCAN
            reviewRequests++
        }
    }

    private fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** Brings the game to the front so the player can start scrolling right away. */
    private fun openGame() {
        packageManager.getLaunchIntentForPackage(GAME_PACKAGE)?.let(::startActivity)
    }

    private companion object {
        const val GAME_PACKAGE = "jp.pokemon.pokemontcgp"
    }
}
