package dev.pocketdeck.adapter.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdChevron
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdIconButtonStyle
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.advice.AdvisorProvider

private const val MODEL_SEPARATOR = ","
private const val EXPANDED_CHEVRON_DEGREES = 90f

/** The models typed or picked for a provider, in the order they are tried. The form keeps them as one comma separated text. */
internal fun modelsOf(text: String): List<String> =
    text.split(MODEL_SEPARATOR).map(String::trim).filter(String::isNotEmpty)

private fun List<String>.asText() = joinToString("$MODEL_SEPARATOR ")

/**
 * The models of one provider as a numbered list, one per row (a comma separated text field was unreadable with several long
 * model names), with a field to add another by name. The order is the order they are asked in.
 */
@Composable
internal fun ModelsEditor(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    actions: SettingsActions,
) {
    val chosen = modelsOf(form.models[provider].orEmpty())
    var expanded by rememberSaveable(provider.name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        ModelsHeader(chosen.size, expanded) { expanded = !expanded }
        if (expanded) {
            ModelsHint(provider, chosen.isEmpty())
            chosen.forEachIndexed { index, model ->
                ModelRow(
                    ModelPlace(index, chosen.size, model),
                    onMove = { actions.onModelChanged(provider, chosen.movedBy(index, it).asText()) },
                    onRemove = {
                        actions.onModelChanged(
                            provider,
                            chosen
                                .filterIndexed {
                                    i,
                                    _,
                                    ->
                                    i != index
                                }.asText(),
                        )
                    },
                )
            }
            AddModel { actions.onModelChanged(provider, (chosen + it).distinct().asText()) }
        } else {
            ModelsSummary(provider, chosen)
        }
    }
}

/** The title row, also the switch that folds the list away; a long list per provider makes the screen long. */
@Composable
private fun ModelsHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(R.string.settings_ai_model)
    val state = stringResource(if (expanded) R.string.settings_ai_models_fold else R.string.settings_ai_models_unfold)
    val turn by animateFloatAsState(if (expanded) EXPANDED_CHEVRON_DEGREES else 0f, label = "models chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = state, role = Role.Button, onClick = onToggle)
            .padding(vertical = PdSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = PocketDesign.typography.titleSm, color = PocketDesign.colors.ink)
        if (count > 0) Text("$count", style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)
        PdChevron(Modifier.rotate(turn))
    }
}

/** What stays visible when the list is folded: the model that is asked first and how many follow it. */
@Composable
private fun ModelsSummary(
    provider: AdvisorProvider,
    chosen: List<String>,
) {
    val first = chosen.firstOrNull() ?: return ModelsHint(provider, noneChosen = true)
    val more =
        if (chosen.size >
            1
        ) {
            "  " + pluralStringResource(R.plurals.settings_ai_models_more, chosen.size - 1, chosen.size - 1)
        } else {
            ""
        }
    Text(first + more, style = PocketDesign.typography.body, color = PocketDesign.colors.ink)
}

@Composable
private fun ModelsHint(
    provider: AdvisorProvider,
    noneChosen: Boolean,
) {
    val text =
        if (noneChosen && provider.defaultModels.isEmpty()) {
            stringResource(R.string.settings_ai_model_none_own)
        } else if (noneChosen) {
            stringResource(R.string.settings_ai_model_default_used, provider.defaultModel)
        } else {
            stringResource(R.string.settings_ai_model_hint)
        }
    Text(text, style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)
}

/** Where a model stands in its provider's list. */
private class ModelPlace(
    val index: Int,
    val count: Int,
    val name: String,
)

private fun List<String>.movedBy(
    index: Int,
    step: Int,
): List<String> = toMutableList().also { it.add(index + step, it.removeAt(index)) }

@Composable
private fun ModelRow(
    place: ModelPlace,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${place.index + 1}.", style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)
        Text(place.name, Modifier.weight(1f), style = PocketDesign.typography.body, color = PocketDesign.colors.ink)
        PdIconButton(
            PdIcons.ArrowUp,
            stringResource(R.string.settings_ai_model_up, place.name),
            { onMove(-1) },
            style = PdIconButtonStyle.Plain,
            enabled = place.index > 0,
        )
        PdIconButton(
            PdIcons.ArrowDown,
            stringResource(R.string.settings_ai_model_down, place.name),
            { onMove(1) },
            style = PdIconButtonStyle.Plain,
            enabled = place.index < place.count - 1,
        )
        PdIconButton(
            PdIcons.Close,
            stringResource(R.string.settings_ai_model_remove, place.name),
            onRemove,
            style = PdIconButtonStyle.Plain,
        )
    }
}

@Composable
private fun AddModel(onAdd: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val canAdd = draft.isNotBlank() && MODEL_SEPARATOR !in draft
    val add = {
        onAdd(draft.trim())
        draft = ""
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.settings_ai_model_add_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canAdd) add() }),
        )
        PdButton(
            stringResource(R.string.settings_ai_model_add),
            add,
            style = PdButtonStyle.Secondary,
            size = PdButtonSize.Small,
            enabled = canAdd,
        )
    }
}
