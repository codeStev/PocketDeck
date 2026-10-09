package dev.pocketdeck.adapter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdRowGroup
import dev.codestev.pocketdesign.components.PdSpinner
import dev.codestev.pocketdesign.components.PdSwitch
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.ListedModel
import dev.pocketdeck.application.advice.ModelCost
import dev.pocketdeck.application.advice.Thinking
import java.util.Locale

/** Lists the models that suit the task, from the service itself; the player picks the ones to try, in order. */
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
        title = stringResource(picker.provider.title()),
        onDismissRequest = actions.onCloseModelPicker,
        confirmText = stringResource(R.string.settings_ai_models_done),
        onConfirm = actions.onCloseModelPicker,
    ) {
        Text(
            stringResource(picker.provider.hint()),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
        if (picker.provider == AdvisorProvider.OPENROUTER) PaidChoice(picker, actions)
        when (val content = picker.content) {
            ModelPickerContent.Loading -> Loading()
            is ModelPickerContent.Failed -> Failed(content, picker, actions)
            is ModelPickerContent.Loaded -> LoadedContent(content, picker, chosen, actions)
        }
    }
}

private fun AdvisorProvider.title() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_models_title_claude
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_models_title
        AdvisorProvider.OPENAI -> R.string.settings_ai_models_title_openai
        AdvisorProvider.OWN_SERVER -> R.string.settings_ai_models_title_own
    }

private fun AdvisorProvider.hint() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_models_hint_claude
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_models_hint
        AdvisorProvider.OPENAI -> R.string.settings_ai_models_hint_openai
        AdvisorProvider.OWN_SERVER -> R.string.settings_ai_models_hint_own
    }

/** Paid models are optional and say so: every request is charged to the player's OpenRouter account. */
@Composable
private fun PaidChoice(
    picker: ModelPicker,
    actions: SettingsActions,
) {
    PdSwitch(picker.includePaid, actions.onIncludePaidChanged, stringResource(R.string.settings_ai_models_include_paid))
    if (picker.includePaid) PdBanner(stringResource(R.string.settings_ai_models_paid_note), kind = PdBannerKind.Warning)
}

@Composable
private fun Failed(
    content: ModelPickerContent.Failed,
    picker: ModelPicker,
    actions: SettingsActions,
) {
    val reason = content.message
    val text =
        if (reason == null && picker.provider.needsKeyToList()) {
            stringResource(R.string.settings_ai_models_key_needed)
        } else {
            stringResource(R.string.settings_ai_models_failed, reason.orEmpty())
        }
    PdBanner(
        text,
        kind = PdBannerKind.Danger,
        actionLabel = stringResource(R.string.settings_ai_models_retry),
        onAction = { actions.onOpenModelPicker(picker.provider) },
    )
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
    var query by rememberSaveable { mutableStateOf("") }
    // Paid lists run to hundreds of models, so they can be searched by name or id.
    PdTextField(
        query,
        { query = it },
        Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.settings_ai_models_search),
        leadingIcon = PdIcons.Search,
    )
    val shown = content.models.filter { it.matches(query) }
    // The row builder is not composable, so the texts are resolved first.
    val texts = shown.associate { it.id to details(it) }
    val defaults = if (chosen.isEmpty()) picker.provider.defaultModels else emptyList()
    Column(Modifier.heightIn(max = LIST_HEIGHT).verticalScroll(rememberScrollState())) {
        PdRowGroup {
            shown.forEach { model ->
                val position = chosen.indexOf(model.id)
                row(
                    title = model.name,
                    onClick = { actions.onToggleModel(picker.provider, model.id) },
                    value = texts.getValue(model.id),
                    trailing = selectionMark(picker, position, model.id in defaults),
                )
            }
        }
    }
}

/** Anthropic and OpenAI list their models only to a key holder. */
private fun AdvisorProvider.needsKeyToList() = this == AdvisorProvider.ANTHROPIC || this == AdvisorProvider.OPENAI

private fun ListedModel.matches(query: String): Boolean =
    query.isBlank() || name.contains(query.trim(), ignoreCase = true) || id.contains(query.trim(), ignoreCase = true)

/** A chip on the chosen models: their place in the order for several, "chosen" for one, "default" for the fallback. */
private fun selectionMark(
    picker: ModelPicker,
    position: Int,
    isDefault: Boolean,
): (@Composable () -> Unit)? =
    when {
        position >= 0 && picker.chooseSeveral -> (
            {
                PdChip((position + 1).toString(), kind = PdChipKind.Selected, leadingIcon = PdIcons.Check)
            }
        )

        position >= 0 -> (
            {
                PdChip(
                    stringResource(R.string.settings_ai_models_chosen),
                    kind = PdChipKind.Selected,
                    leadingIcon = PdIcons.Check,
                )
            }
        )

        isDefault -> ({ PdChip(stringResource(R.string.settings_ai_models_default)) })

        else -> null
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
    // OpenAI and a server of the player's own state neither context nor thinking; the id says it all.
    val facts = if (model.contextTokens > 0) "$context · $thinking$expires" else null
    return listOfNotNull(model.id, facts, priceLine(model)).joinToString("\n")
}

/** What the model costs, as the service states it: free, or dollars per million tokens and about one deck's worth. */
@Composable
private fun priceLine(model: ListedModel): String? {
    if (model.free) return stringResource(R.string.settings_ai_price_free)
    val prompt = model.promptPrice ?: return null
    val answer = model.completionPrice ?: return null
    val perDeck = ModelCost.perDeck(model) ?: return null
    return stringResource(R.string.settings_ai_price, money(prompt), money(answer), money(perDeck))
}

/** Cents for what a deck costs, and enough digits to tell cheap models apart. */
private fun money(dollars: Double): String =
    String.format(Locale.getDefault(), if (dollars < SMALL_AMOUNT) "%.3f" else "%.2f", dollars)

private val LIST_HEIGHT = 380.dp
private const val TOKENS_PER_K = 1000
private const val SMALL_AMOUNT = 0.1
