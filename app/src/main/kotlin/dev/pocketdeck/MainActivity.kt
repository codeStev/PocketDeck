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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.pocketdeck.adapter.capture.AutoScroll
import dev.pocketdeck.adapter.capture.OverlayPermission
import dev.pocketdeck.adapter.capture.RequestScreenCapture
import dev.pocketdeck.adapter.capture.ScanCapture
import dev.pocketdeck.adapter.ui.AppTab
import dev.pocketdeck.adapter.ui.PocketDeckApp
import dev.pocketdeck.adapter.ui.about.DeviceInfo
import dev.pocketdeck.adapter.ui.about.DiagnosticsLog
import dev.pocketdeck.adapter.ui.about.DiagnosticsReport
import dev.pocketdeck.adapter.ui.chase.ChaseViewModel
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.adapter.ui.decks.DecksViewModel
import dev.pocketdeck.adapter.ui.files.CollectionFilesViewModel
import dev.pocketdeck.adapter.ui.profile.ProfilesViewModel
import dev.pocketdeck.adapter.ui.scan.ScanViewModel
import dev.pocketdeck.adapter.ui.settings.AboutActions
import dev.pocketdeck.adapter.ui.settings.SettingsViewModel
import dev.pocketdeck.adapter.ui.theme.PocketDeckTheme
import dev.pocketdeck.adapter.ui.welcome.WelcomeFlow
import dev.pocketdeck.adapter.ui.welcome.WelcomeViewModel
import dev.pocketdeck.domain.card.Language
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var language: Language

    @Inject
    lateinit var collectionViewModelFactory: CollectionViewModel.Factory

    @Inject
    lateinit var decksViewModelFactory: DecksViewModel.Factory

    @Inject
    lateinit var chaseViewModelFactory: ChaseViewModel.Factory

    @Inject
    lateinit var scanViewModelFactory: ScanViewModel.Factory

    @Inject
    lateinit var settingsViewModelFactory: SettingsViewModel.Factory

    @Inject
    lateinit var profilesViewModelFactory: ProfilesViewModel.Factory

    @Inject
    lateinit var diagnosticsLog: DiagnosticsLog

    @Inject
    lateinit var filesViewModelFactory: CollectionFilesViewModel.Factory

    @Inject
    lateinit var welcomeViewModelFactory: WelcomeViewModel.Factory

    private var selectedTab by mutableStateOf(AppTab.COLLECTION)
    private var reviewRequests by mutableIntStateOf(0)
    private var reviewIsComplete = false
    private var autoScrollEnabled by mutableStateOf(false)

    private var pendingAutomatic = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            PocketDeckTheme {
                val collectionViewModel =
                    viewModel<CollectionViewModel>(
                        key = "collection-${language.name}",
                        factory = collectionViewModelFactory,
                    )
                val decksViewModel =
                    viewModel<DecksViewModel>(key = "decks-${language.name}", factory = decksViewModelFactory)
                val chaseViewModel =
                    viewModel<ChaseViewModel>(key = "chase-${language.name}", factory = chaseViewModelFactory)
                val scanViewModel =
                    viewModel<ScanViewModel>(key = "scan-${language.name}", factory = scanViewModelFactory)
                val settingsViewModel = viewModel<SettingsViewModel>(factory = settingsViewModelFactory)
                val profilesViewModel = viewModel<ProfilesViewModel>(factory = profilesViewModelFactory)
                val filesViewModel = viewModel<CollectionFilesViewModel>(factory = filesViewModelFactory)
                val welcomeViewModel = viewModel<WelcomeViewModel>(factory = welcomeViewModelFactory)
                val welcome by welcomeViewModel.state.collectAsStateWithLifecycle()
                var tourRequested by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(reviewRequests) {
                    if (reviewRequests >
                        0
                    ) {
                        scanViewModel.onReviewRequested(reviewIsComplete)
                    }
                }

                val captureConsent =
                    rememberLauncherForActivityResult(RequestScreenCapture()) { consent ->
                        if (consent != null) {
                            ScanCapture.start(this@MainActivity, consent, pendingAutomatic)
                        }
                    }
                val notificationPermission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        // The scan works without the notification being visible, so a refusal does not stop it.
                        captureConsent.launch(Unit)
                    }
                val overlaySettings =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

                // Nothing is shown until storage has answered, so the tour does not flash up for a returning player.
                val firstRun = welcome.agreed == false
                if (firstRun || tourRequested) {
                    WelcomeFlow(firstRun = firstRun) {
                        if (firstRun) {
                            welcomeViewModel.onAgree()
                        } else {
                            tourRequested =
                                false
                        }
                    }
                    return@PocketDeckTheme
                }
                if (welcome.agreed == null) return@PocketDeckTheme
                PocketDeckApp(
                    collectionViewModel = collectionViewModel,
                    decksViewModel = decksViewModel,
                    chaseViewModel = chaseViewModel,
                    scanViewModel = scanViewModel,
                    settingsViewModel = settingsViewModel,
                    filesViewModel = filesViewModel,
                    profilesViewModel = profilesViewModel,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    autoScrollEnabled = autoScrollEnabled,
                    onOpenAccessibilitySettings = { startActivity(AutoScroll.settingsIntent()) },
                    about =
                        AboutActions(
                            versionName = versionName(),
                            diagnostics = ::diagnostics,
                            onOpenAccessibilitySettings = { startActivity(AutoScroll.settingsIntent()) },
                            onShowWelcome = { tourRequested = true },
                        ),
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
        // Android connects a service that was just switched on a moment after we return here.
        window.decorView.postDelayed({ autoScrollEnabled = AutoScroll.isEnabled(this) }, SERVICE_CONNECT_WAIT_MILLIS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ScanCapture.ACTION_REVIEW) {
            selectedTab = AppTab.SCAN
            reviewIsComplete = intent.getBooleanExtra(ScanCapture.EXTRA_COMPLETE, false)
            reviewRequests++
        }
    }

    /** What a player pastes into a problem report: the phone's facts and the app's last notes, never keys or cards. */
    private fun diagnostics(): String {
        val metrics = resources.displayMetrics
        val info =
            DeviceInfo(
                appVersion = "${versionName()} (${packageManager.getPackageInfo(packageName, 0).longVersionCode})",
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                screen =
                    "${metrics.widthPixels}x${metrics.heightPixels} px, ${metrics.densityDpi} dpi, " +
                        "font scale ${resources.configuration.fontScale}",
                language = Locale.getDefault().toLanguageTag(),
                autoScroll = "switched on: ${AutoScroll.isSwitchedOn(this)}, connected: ${AutoScroll.isConnected()}",
                permissions =
                    "overlay ${OverlayPermission.isGranted(this)}, notifications ${!needsNotificationPermission()}",
            )
        return DiagnosticsReport.format(info, diagnosticsLog.lines())
    }

    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val SERVICE_CONNECT_WAIT_MILLIS = 1_000L
    }
}
