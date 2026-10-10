package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdChevron
import dev.codestev.pocketdesign.components.PdDivider
import dev.codestev.pocketdesign.components.PdRowGroup
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.about.AboutPanel
import dev.pocketdeck.adapter.ui.files.CollectionFilesSection
import dev.pocketdeck.adapter.ui.files.CollectionFilesViewModel
import dev.pocketdeck.application.advice.AdvisorProvider

private val SAVE_BAR_ROOM = 72.dp

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    files: CollectionFilesViewModel,
    about: AboutActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state,
        files,
        about,
        SettingsActions(
            viewModel::onKeyChanged,
            viewModel::onModelChanged,
            viewModel::onMove,
            viewModel::onSave,
            viewModel::onOpenModelPicker,
            viewModel::onToggleModel,
            viewModel::onCloseModelPicker,
            viewModel::onIncludePaidChanged,
            viewModel::onAddressChanged,
            viewModel::onPlainHttpChanged,
        ),
    )
}

/** What the player can do on the settings screen. */
internal class SettingsActions(
    val onKeyChanged: (AdvisorProvider, String) -> Unit,
    val onModelChanged: (AdvisorProvider, String) -> Unit,
    val onMove: (AdvisorProvider, Int) -> Unit,
    val onSave: () -> Unit,
    val onOpenModelPicker: (AdvisorProvider) -> Unit,
    val onToggleModel: (AdvisorProvider, String) -> Unit,
    val onCloseModelPicker: () -> Unit,
    val onIncludePaidChanged: (Boolean) -> Unit,
    val onAddressChanged: (AdvisorProvider, String) -> Unit,
    val onPlainHttpChanged: (Boolean) -> Unit,
)

/** The settings, in sections; "AI" is the first. A new section is another composable in this column. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    files: CollectionFilesViewModel,
    about: AboutActions,
    actions: SettingsActions,
) {
    state.modelPicker?.let { ModelPickerDialog(it, state.form, actions) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        AboutPanel(about.versionName, about.onOpenAccessibilitySettings, about.onShowWelcome) {
            showAbout =
                false
        }
    }
    Box(Modifier.fillMaxSize()) {
        SettingsColumn(state, files, about, actions) { showAbout = true }
        SaveBar(
            state,
            actions.onSave,
            Modifier
                .align(
                    Alignment.BottomCenter,
                ).imePadding()
                .padding(horizontal = PdSpacing.space4, vertical = PdSpacing.space2),
        )
    }
}

@Composable
private fun SettingsColumn(
    state: SettingsUiState,
    files: CollectionFilesViewModel,
    about: AboutActions,
    actions: SettingsActions,
    onShowAbout: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PdSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = PocketDesign.typography.titleLg,
            color = PocketDesign.colors.ink,
            modifier = Modifier.padding(top = PdSpacing.space2),
        )
        // A line and some air between the sections: the AI section alone is long, and they must not run into each other.
        LanguageSection()
        SectionBreak()
        AiSection(state, actions)
        SectionBreak()
        CollectionFilesSection(files)
        SectionBreak()
        SupportSection(about.diagnostics)
        SectionBreak()
        val aboutTitle = stringResource(R.string.settings_about)
        val aboutValue = stringResource(R.string.settings_about_value, about.versionName)
        PdRowGroup {
            row(aboutTitle, onClick = onShowAbout, value = aboutValue, trailing = { PdChevron() })
        }
        // Room for the pinned save bar, so it never hides the last row.
        Spacer(Modifier.height(SAVE_BAR_ROOM))
    }
}

@Composable
private fun SectionBreak() {
    Spacer(Modifier.height(PdSpacing.space2))
    PdDivider()
    Spacer(Modifier.height(PdSpacing.space2))
}

/** What the About panel needs from the app around it. */
class AboutActions(
    val versionName: String,
    /** The text of the diagnostics a player can copy; built when they ask, from the app's own state. */
    val diagnostics: () -> String,
    val onOpenAccessibilitySettings: () -> Unit,
    val onShowWelcome: () -> Unit,
)

@Composable
private fun AiSection(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        PdSectionHeader(stringResource(R.string.settings_ai_title))
        Text(
            stringResource(R.string.settings_ai_hint),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
        )
        PdBanner(stringResource(R.string.settings_ai_cost_warning), kind = PdBannerKind.Warning)
        state.form.order.forEachIndexed { index, provider ->
            ProviderCard(provider, state.form, index, actions)
        }
    }
}

/**
 * Pinned above the navigation while there is something to save: a key typed at the top of a long screen needs no scroll to
 * find the button. After saving it says so, until the next edit.
 */
@Composable
private fun SaveBar(
    state: SettingsUiState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.changed) {
        PdCard(modifier.fillMaxWidth(), contentPadding = PdSpacing.space3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_unsaved),
                    Modifier.weight(1f),
                    style = PocketDesign.typography.body,
                    color = PocketDesign.colors.ink,
                )
                PdButton(stringResource(R.string.settings_save), onSave)
            }
        }
    } else if (state.justSaved) {
        PdBanner(stringResource(R.string.settings_saved), modifier.fillMaxWidth(), kind = PdBannerKind.Success)
    }
}
