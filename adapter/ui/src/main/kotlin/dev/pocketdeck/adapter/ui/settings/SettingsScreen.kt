package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdChevron
import dev.codestev.pocketdesign.components.PdRowGroup
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.about.AboutPanel
import dev.pocketdeck.application.advice.AdvisorProvider

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    about: AboutActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state,
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
)

/** The settings, in sections; "AI" is the first. A new section is another composable in this column. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    about: AboutActions,
    actions: SettingsActions,
) {
    state.modelPicker?.let { ModelPickerDialog(it, state.form, actions) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        AboutPanel(about.versionName, about.onOpenAccessibilitySettings, about.onShowWelcome) { showAbout = false }
    }
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
        LanguageSection()
        AiSection(state, actions)
        val aboutTitle = stringResource(R.string.settings_about)
        val aboutValue = stringResource(R.string.settings_about_value, about.versionName)
        PdRowGroup {
            row(aboutTitle, onClick = { showAbout = true }, value = aboutValue, trailing = { PdChevron() })
        }
        Spacer(Modifier.height(PdSpacing.space4))
    }
}

/** What the About panel needs from the app around it. */
class AboutActions(
    val versionName: String,
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
        state.form.order.forEachIndexed { index, provider ->
            ProviderCard(provider, state.form, index, actions)
        }
        PdButton(
            stringResource(R.string.settings_save),
            actions.onSave,
            Modifier.fillMaxWidth(),
            enabled = state.changed,
        )
        if (state.justSaved) PdBanner(stringResource(R.string.settings_saved), kind = PdBannerKind.Success)
    }
}

@Composable
private fun ProviderCard(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    index: Int,
    actions: SettingsActions,
) {
    PdCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            Text(
                stringResource(provider.title()),
                style = PocketDesign.typography.titleMd,
                color = PocketDesign.colors.ink,
            )
            Text(
                stringResource(provider.hint()),
                style = PocketDesign.typography.bodySm,
                color = PocketDesign.colors.inkMuted,
            )
            PdTextField(
                value = form.keys[provider].orEmpty(),
                onValueChange = { actions.onKeyChanged(provider, it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.settings_ai_key),
                visualTransformation = PasswordVisualTransformation(),
            )
            PdTextField(
                value = form.models[provider].orEmpty(),
                onValueChange = { actions.onModelChanged(provider, it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = provider.defaultModel,
                supportingText = stringResource(R.string.settings_ai_model_hint),
            )
            PdButton(
                stringResource(provider.browseLabel()),
                { actions.onOpenModelPicker(provider) },
                Modifier.fillMaxWidth(),
                style = PdButtonStyle.Tonal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
                PdButton(
                    stringResource(R.string.settings_ai_earlier),
                    { actions.onMove(provider, -1) },
                    Modifier.weight(1f),
                    style = PdButtonStyle.Secondary,
                    size = PdButtonSize.Small,
                    enabled = index > 0,
                )
                PdButton(
                    stringResource(R.string.settings_ai_later),
                    { actions.onMove(provider, 1) },
                    Modifier.weight(1f),
                    style = PdButtonStyle.Secondary,
                    size = PdButtonSize.Small,
                    enabled = index < form.order.lastIndex,
                )
            }
        }
    }
}

private fun AdvisorProvider.browseLabel() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_browse_claude
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_browse_models
    }

private fun AdvisorProvider.title() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_provider_anthropic
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_provider_openrouter
    }

private fun AdvisorProvider.hint() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_provider_anthropic_hint
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_provider_openrouter_hint
    }
