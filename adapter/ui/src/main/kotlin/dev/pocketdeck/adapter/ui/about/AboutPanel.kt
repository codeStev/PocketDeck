package dev.pocketdeck.adapter.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdFullScreenPanel
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import kotlinx.coroutines.launch

/** What the app is, who it is not from, under which licence it is open, where its data comes from, and what it sends. */
@Composable
fun AboutPanel(
    versionName: String,
    onOpenAccessibilitySettings: () -> Unit,
    onShowWelcome: () -> Unit,
    onClose: () -> Unit,
) {
    var showLicence by rememberSaveable { mutableStateOf(false) }
    if (showLicence) {
        LicencePanel { showLicence = false }
        return
    }
    PdFullScreenPanel(stringResource(R.string.about_title), stringResource(R.string.decks_code_close), onClose) {
        val links = LocalUriHandler.current
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PdSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
        ) {
            Header(versionName)
            Section(R.string.about_open_source_title, R.string.about_open_source_text) {
                PdButton(
                    stringResource(R.string.about_source_code),
                    { links.openUri(Links.REPOSITORY) },
                    Modifier.fillMaxWidth(),
                )
                PdButton(
                    stringResource(R.string.about_licence_text),
                    { showLicence = true },
                    Modifier.fillMaxWidth(),
                    style = PdButtonStyle.Secondary,
                )
            }
            Credits()
            Section(R.string.about_autoscroll_title, R.string.about_autoscroll_text) {
                PdButton(
                    stringResource(R.string.about_open_accessibility),
                    onOpenAccessibilitySettings,
                    Modifier.fillMaxWidth(),
                    style = PdButtonStyle.Secondary,
                )
            }
            Section(R.string.about_privacy_title, R.string.about_privacy_text) {
                PdButton(
                    stringResource(R.string.about_privacy_link),
                    { links.openUri(Links.PRIVACY) },
                    Modifier.fillMaxWidth(),
                    style = PdButtonStyle.Secondary,
                )
            }
            PdButton(
                stringResource(R.string.about_tour),
                onShowWelcome,
                Modifier.fillMaxWidth().padding(bottom = PdSpacing.space4),
                style = PdButtonStyle.Tonal,
            )
        }
    }
}

@Composable
private fun Header(versionName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        Text(
            stringResource(R.string.about_app_name),
            style = PocketDesign.typography.titleLg,
            color = PocketDesign.colors.ink,
        )
        Text(
            stringResource(R.string.about_version, versionName),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
        Text(
            stringResource(R.string.about_description),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.ink,
        )
        Text(
            stringResource(R.string.welcome_unofficial),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
    }
}

@Composable
private fun Section(
    title: Int,
    text: Int,
    actions: @Composable () -> Unit,
) {
    PdCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            Text(stringResource(title), style = PocketDesign.typography.titleMd, color = PocketDesign.colors.ink)
            Text(stringResource(text), style = PocketDesign.typography.body, color = PocketDesign.colors.inkMuted)
            actions()
        }
    }
}

@Composable
private fun Credits() {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        PdSectionHeader(stringResource(R.string.about_credits_title))
        PdCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
                CREDITS.forEach { credit ->
                    Column {
                        Text(credit.name, style = PocketDesign.typography.titleSm, color = PocketDesign.colors.ink)
                        Text(
                            "${stringResource(credit.what)} · ${credit.licence}",
                            style = PocketDesign.typography.bodySm,
                            color = PocketDesign.colors.inkMuted,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.about_pocketdecks_note),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
    }
}

/** The licence text ships inside the app, so it opens without a connection. */
@Composable
private fun LicencePanel(onClose: () -> Unit) {
    val resources = LocalResources.current
    val text =
        remember {
            resources
                .openRawResource(R.raw.license_agpl)
                .bufferedReader()
                .use { it.readText() }
        }
    PdFullScreenPanel(stringResource(R.string.about_licence_text), stringResource(R.string.decks_code_close), onClose) {
        Text(
            text,
            Modifier.verticalScroll(rememberScrollState()).padding(PdSpacing.space4),
            style =
                PocketDesign.typography.bodySm.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = LICENCE_FONT_SIZE,
                ),
            color = PocketDesign.colors.ink,
        )
    }
}

private val LICENCE_FONT_SIZE = 11.sp
