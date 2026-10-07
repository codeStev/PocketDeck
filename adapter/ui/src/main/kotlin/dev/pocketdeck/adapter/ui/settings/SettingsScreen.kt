package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.advice.AdvisorProvider

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state,
        SettingsActions(
            viewModel::onKeyChanged,
            viewModel::onModelChanged,
            viewModel::onMove,
            viewModel::onSave,
            viewModel::onOpenModelPicker,
            viewModel::onToggleModel,
            viewModel::onCloseModelPicker,
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
)

/** The settings, in sections; "AI" is the first. A new section is another composable in this column. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    state.modelPicker?.let { ModelPickerDialog(it, state.form, actions) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        LanguageSection()
        AiSection(state, actions)
    }
}

@Composable
private fun AiSection(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    Text(stringResource(R.string.settings_ai_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.settings_ai_hint), style = MaterialTheme.typography.bodyMedium)
    state.form.order.forEachIndexed { index, provider ->
        ProviderCard(provider, state.form, index, actions)
    }
    Button(onClick = actions.onSave, enabled = state.changed, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_save))
    }
    if (state.justSaved) Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ProviderCard(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    index: Int,
    actions: SettingsActions,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(provider.title()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(provider.hint()), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = form.keys[provider].orEmpty(),
                onValueChange = { actions.onKeyChanged(provider, it) },
                label = { Text(stringResource(R.string.settings_ai_key)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.models[provider].orEmpty(),
                onValueChange = { actions.onModelChanged(provider, it) },
                label = { Text(stringResource(R.string.settings_ai_model)) },
                placeholder = { Text(provider.defaultModel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider == AdvisorProvider.OPENROUTER) {
                TextButton(onClick = { actions.onOpenModelPicker(provider) }) {
                    Text(stringResource(R.string.settings_ai_browse_models))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { actions.onMove(provider, -1) }, enabled = index > 0) {
                    Text(stringResource(R.string.settings_ai_earlier))
                }
                TextButton(onClick = { actions.onMove(provider, 1) }, enabled = index < form.order.lastIndex) {
                    Text(stringResource(R.string.settings_ai_later))
                }
            }
        }
    }
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
