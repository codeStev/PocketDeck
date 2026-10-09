package dev.pocketdeck.adapter.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdChevron
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdIconButtonStyle
import dev.codestev.pocketdesign.components.PdSwitch
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.advice.AdvisorProvider

private const val EXPANDED_CHEVRON_DEGREES = 90f

/** Whether the provider can be asked as the form stands: a key, or for the player's own server an address and a model. */
internal fun AiSettingsForm.isReady(provider: AdvisorProvider): Boolean =
    if (provider.hasOwnAddress) {
        addresses[provider].orEmpty().isNotBlank() && models[provider].orEmpty().isNotBlank()
    } else {
        keys[provider].orEmpty().isNotBlank()
    }

/**
 * One AI service as a card that folds away: with four of them the settings would be a long page, so only the title, the
 * state ("Ready" or not) and the place in the asking order show until the player opens it.
 */
@Composable
internal fun ProviderCard(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    index: Int,
    actions: SettingsActions,
) {
    var expanded by rememberSaveable(provider.name) { mutableStateOf(false) }
    PdCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            ProviderHeader(provider, form, index, expanded, actions) { expanded = !expanded }
            if (expanded) ProviderBody(provider, form, actions)
        }
    }
}

@Composable
private fun ProviderHeader(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    index: Int,
    expanded: Boolean,
    actions: SettingsActions,
    onToggle: () -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) EXPANDED_CHEVRON_DEGREES else 0f, label = "provider chevron")
    val state =
        stringResource(if (expanded) R.string.settings_ai_provider_fold else R.string.settings_ai_provider_unfold)
    val ready = form.isReady(provider)
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = state, role = Role.Button, onClick = onToggle),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space2),
    ) {
        Text(
            "${index + 1}. " + stringResource(provider.title()),
            style = PocketDesign.typography.titleMd,
            color = PocketDesign.colors.ink,
        )
        // One line of state and controls: the chip, then the order arrows and the fold chevron together at the end.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PdChip(
                stringResource(if (ready) R.string.settings_ai_status_ready else R.string.settings_ai_status_off),
                kind = if (ready) PdChipKind.Success else PdChipKind.Default,
            )
            Spacer(Modifier.weight(1f))
            OrderArrows(provider, index, form.order.lastIndex, actions)
            PdChevron(Modifier.padding(horizontal = PdSpacing.space3).rotate(turn))
        }
    }
}

/** The place in the asking order is changed from the closed card: the order is the point of having several providers. */
@Composable
private fun OrderArrows(
    provider: AdvisorProvider,
    index: Int,
    last: Int,
    actions: SettingsActions,
) {
    val name = stringResource(provider.title())
    PdIconButton(
        PdIcons.ArrowUp,
        stringResource(R.string.settings_ai_provider_earlier, name),
        { actions.onMove(provider, -1) },
        style = PdIconButtonStyle.Plain,
        enabled = index > 0,
    )
    PdIconButton(
        PdIcons.ArrowDown,
        stringResource(R.string.settings_ai_provider_later, name),
        { actions.onMove(provider, 1) },
        style = PdIconButtonStyle.Plain,
        enabled = index < last,
    )
}

@Composable
private fun ProviderBody(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    actions: SettingsActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        Text(
            stringResource(provider.hint()),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
        if (provider.hasOwnAddress) ServerAddress(provider, form, actions)
        KeyField(provider, form.keys[provider].orEmpty(), actions)
        ModelsEditor(provider, form, actions)
        PdButton(
            stringResource(provider.browseLabel()),
            { actions.onOpenModelPicker(provider) },
            Modifier.fillMaxWidth(),
            style = PdButtonStyle.Tonal,
        )
    }
}

@Composable
private fun KeyField(
    provider: AdvisorProvider,
    key: String,
    actions: SettingsActions,
) {
    val focusManager = LocalFocusManager.current
    PdTextField(
        value = key,
        onValueChange = { actions.onKeyChanged(provider, it) },
        modifier = Modifier.fillMaxWidth(),
        placeholder =
            stringResource(
                if (provider.keyIsOptional) R.string.settings_ai_key_optional else R.string.settings_ai_key,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    // The keyboard's Done key saves, so nobody has to find the button behind the keyboard.
                    actions.onSave()
                    focusManager.clearFocus()
                },
            ),
        visualTransformation = PasswordVisualTransformation(),
    )
}

/** The address of a server of the player's own, and the plain http choice. */
@Composable
private fun ServerAddress(
    provider: AdvisorProvider,
    form: AiSettingsForm,
    actions: SettingsActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        PdTextField(
            value = form.addresses[provider].orEmpty(),
            onValueChange = { actions.onAddressChanged(provider, it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.settings_ai_address),
            supportingText = stringResource(R.string.settings_ai_address_hint),
        )
        PdSwitch(
            form.plainHttp,
            actions.onPlainHttpChanged,
            stringResource(R.string.settings_ai_plain_http),
            supportingText = stringResource(R.string.settings_ai_plain_http_hint),
        )
    }
}

private fun AdvisorProvider.browseLabel() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_browse_claude
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_browse_models
        AdvisorProvider.OPENAI -> R.string.settings_ai_browse_openai
        AdvisorProvider.OWN_SERVER -> R.string.settings_ai_browse_own
    }

private fun AdvisorProvider.title() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_provider_anthropic
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_provider_openrouter
        AdvisorProvider.OPENAI -> R.string.settings_ai_provider_openai
        AdvisorProvider.OWN_SERVER -> R.string.settings_ai_provider_own
    }

private fun AdvisorProvider.hint() =
    when (this) {
        AdvisorProvider.ANTHROPIC -> R.string.settings_ai_provider_anthropic_hint
        AdvisorProvider.OPENROUTER -> R.string.settings_ai_provider_openrouter_hint
        AdvisorProvider.OPENAI -> R.string.settings_ai_provider_openai_hint
        AdvisorProvider.OWN_SERVER -> R.string.settings_ai_provider_own_hint
    }
