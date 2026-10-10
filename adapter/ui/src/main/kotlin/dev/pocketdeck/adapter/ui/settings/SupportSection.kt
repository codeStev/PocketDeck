package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.about.Links
import kotlinx.coroutines.launch

/** Help on the settings screen: the diagnostics go to the clipboard after saying what they hold, and the issue page opens. */
@Composable
internal fun SupportSection(diagnostics: () -> String) {
    val links = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var asking by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }
    if (asking) {
        PdDialog(
            title = stringResource(R.string.settings_support_dialog_title),
            onDismissRequest = { asking = false },
            confirmText = stringResource(R.string.settings_support_copy),
            onConfirm = {
                asking = false
                copied = true
                scope.launch {
                    clipboard.setClipEntry(
                        android.content.ClipData
                            .newPlainText("PocketDeck diagnostics", diagnostics())
                            .toClipEntry(),
                    )
                }
            },
            dismissText = stringResource(R.string.decks_cancel),
        ) {
            Text(
                stringResource(R.string.settings_support_dialog_text),
                style = PocketDesign.typography.body,
                color = PocketDesign.colors.ink,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        PdSectionHeader(stringResource(R.string.settings_support_title))
        Text(
            stringResource(R.string.settings_support_text),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
        )
        PdButton(
            stringResource(R.string.settings_support_copy),
            { asking = true },
            Modifier.fillMaxWidth(),
            style = PdButtonStyle.Secondary,
        )
        PdButton(
            stringResource(R.string.settings_support_report),
            { links.openUri(Links.ISSUES) },
            Modifier.fillMaxWidth(),
        )
        if (copied) PdBanner(stringResource(R.string.settings_support_copied), kind = PdBannerKind.Success)
    }
}
