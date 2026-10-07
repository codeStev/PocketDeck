package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.advice.ListedModel
import dev.pocketdeck.application.advice.Thinking

/** Lists the free models that suit the task, from the service itself; the player picks the ones to try, in order. */
@Composable
internal fun ModelPickerDialog(
    picker: ModelPicker,
    form: AiSettingsForm,
    actions: SettingsActions,
) {
    val chosen =
        form.models[picker.provider]
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = actions.onCloseModelPicker,
        title = { Text(stringResource(R.string.settings_ai_models_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_ai_models_hint), style = MaterialTheme.typography.bodySmall)
                when (val content = picker.content) {
                    ModelPickerContent.Loading -> Text(stringResource(R.string.settings_ai_models_loading))
                    is ModelPickerContent.Failed -> FailedContent(content, picker, actions)
                    is ModelPickerContent.Loaded -> LoadedContent(content, picker, chosen, actions)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onCloseModelPicker) { Text(stringResource(R.string.settings_ai_models_done)) }
        },
    )
}

@Composable
private fun FailedContent(
    content: ModelPickerContent.Failed,
    picker: ModelPicker,
    actions: SettingsActions,
) {
    Text(
        stringResource(R.string.settings_ai_models_failed, content.message.orEmpty()),
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = {
        actions.onOpenModelPicker(picker.provider)
    }) { Text(stringResource(R.string.settings_ai_models_retry)) }
}

@Composable
private fun LoadedContent(
    content: ModelPickerContent.Loaded,
    picker: ModelPicker,
    chosen: List<String>,
    actions: SettingsActions,
) {
    if (content.models.isEmpty()) {
        Text(stringResource(R.string.settings_ai_models_none))
        return
    }
    LazyColumn(Modifier.heightIn(max = LIST_HEIGHT)) {
        items(content.models, key = { it.id }) { model ->
            ModelRow(model, chosen.indexOf(model.id)) { actions.onToggleModel(picker.provider, model.id) }
        }
    }
}

@Composable
private fun ModelRow(
    model: ListedModel,
    position: Int,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = position >= 0, onCheckedChange = { onToggle() })
        Column(Modifier.padding(start = 4.dp)) {
            val order = if (position >= 0) "${position + 1}. " else ""
            Text("$order${model.name}", style = MaterialTheme.typography.bodyLarge)
            Text(details(model), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun details(model: ListedModel): String {
    val thinking =
        stringResource(
            when (model.thinking) {
                Thinking.ANSWERS_DIRECTLY -> R.string.settings_ai_thinking_direct
                Thinking.THINKS_BY_DEFAULT -> R.string.settings_ai_thinking_default
                Thinking.MUST_THINK -> R.string.settings_ai_thinking_must
            },
        )
    val context = stringResource(R.string.settings_ai_context, model.contextTokens / TOKENS_PER_K)
    val expires = model.expiresOn?.let { " · " + stringResource(R.string.settings_ai_expires, it) }.orEmpty()
    return "${model.id}\n$context · $thinking$expires"
}

private val LIST_HEIGHT = 380.dp
private const val TOKENS_PER_K = 1000
