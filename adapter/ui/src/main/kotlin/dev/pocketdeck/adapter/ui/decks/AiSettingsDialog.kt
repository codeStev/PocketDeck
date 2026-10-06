package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.advice.AdvisorProvider

/** Where the player enters the keys of the AI services, picks the models and sets which one is asked first. */
@Composable
internal fun AiSettingsDialog(
    initial: AiSettingsForm,
    onSave: (AiSettingsForm) -> Unit,
    onClose: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.decks_ai_settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.decks_ai_settings_hint), style = MaterialTheme.typography.bodyMedium)
                form.order.forEachIndexed { index, provider ->
                    ProviderSection(provider, form, index, { form = it })
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(form) }) { Text(stringResource(R.string.decks_ai_save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.decks_ai_cancel)) } },
    )
}

@Composable
private fun ProviderSection(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    index: Int,
    onChange: (AiSettingsForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(provider.title()), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(provider.hint()), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = form.keys[provider].orEmpty(),
            onValueChange = { onChange(form.copy(keys = form.keys + (provider to it))) },
            label = { Text(stringResource(R.string.decks_ai_key)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.models[provider].orEmpty(),
            onValueChange = { onChange(form.copy(models = form.models + (provider to it))) },
            label = { Text(stringResource(R.string.decks_ai_model)) },
            placeholder = { Text(provider.defaultModel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(form.copy(order = form.order.moved(index, -1))) }, enabled = index > 0) {
                Text(stringResource(R.string.decks_ai_earlier))
            }
            TextButton(
                onClick = { onChange(form.copy(order = form.order.moved(index, 1))) },
                enabled =
                    index < form.order.lastIndex,
            ) {
                Text(stringResource(R.string.decks_ai_later))
            }
        }
    }
}

private fun <T> List<T>.moved(
    index: Int,
    by: Int,
): List<T> = toMutableList().also { it.add(index + by, it.removeAt(index)) }

private fun AdvisorProvider.title() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.decks_ai_provider_anthropic
        AdvisorProvider.OPENROUTER -> R.string.decks_ai_provider_openrouter
    }

private fun AdvisorProvider.hint() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.decks_ai_provider_anthropic_hint
        AdvisorProvider.OPENROUTER -> R.string.decks_ai_provider_openrouter_hint
    }
