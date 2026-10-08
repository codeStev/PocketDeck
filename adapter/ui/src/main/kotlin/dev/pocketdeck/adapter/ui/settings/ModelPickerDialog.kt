package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdRowGroup
import dev.codestev.pocketdesign.components.PdSpinner
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
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
    PdDialog(
        title = stringResource(R.string.settings_ai_models_title),
        onDismissRequest = actions.onCloseModelPicker,
        confirmText = stringResource(R.string.settings_ai_models_done),
        onConfirm = actions.onCloseModelPicker,
    ) {
        Text(
            stringResource(R.string.settings_ai_models_hint),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
        when (val content = picker.content) {
            ModelPickerContent.Loading -> {
                Loading()
            }

            is ModelPickerContent.Failed -> {
                PdBanner(
                    stringResource(R.string.settings_ai_models_failed, content.message.orEmpty()),
                    kind = PdBannerKind.Danger,
                    actionLabel = stringResource(R.string.settings_ai_models_retry),
                    onAction = { actions.onOpenModelPicker(picker.provider) },
                )
            }

            is ModelPickerContent.Loaded -> {
                LoadedContent(content, picker, chosen, actions)
            }
        }
    }
}

@Composable
private fun Loading() {
    PdSpinner(size = 32.dp)
    Text(
        stringResource(R.string.settings_ai_models_loading),
        style = PocketDesign.typography.body,
        color = PocketDesign.colors.inkMuted,
    )
}

@Composable
private fun ColumnScope.LoadedContent(
    content: ModelPickerContent.Loaded,
    picker: ModelPicker,
    chosen: List<String>,
    actions: SettingsActions,
) {
    if (content.models.isEmpty()) {
        Text(stringResource(R.string.settings_ai_models_none), style = PocketDesign.typography.body)
        return
    }
    // A selected row shows its place in the order the models are tried in.
    // The row builder is not composable, so the texts are resolved first.
    val texts = content.models.associate { it.id to details(it) }
    Column(Modifier.heightIn(max = LIST_HEIGHT).verticalScroll(rememberScrollState())) {
        PdRowGroup {
            content.models.forEach { model ->
                val position = chosen.indexOf(model.id)
                row(
                    title = model.name,
                    onClick = { actions.onToggleModel(picker.provider, model.id) },
                    value = texts.getValue(model.id),
                    trailing = if (position >= 0) ({ OrderChip(position + 1) }) else null,
                )
            }
        }
    }
}

@Composable
private fun OrderChip(position: Int) =
    PdChip(position.toString(), kind = PdChipKind.Selected, leadingIcon = PdIcons.Check)

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
