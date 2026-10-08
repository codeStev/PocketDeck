package dev.pocketdeck.adapter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import dev.pocketdeck.application.advice.ListSuitableModels
import dev.pocketdeck.application.advice.ListedModel
import dev.pocketdeck.application.advice.ModelRequest
import dev.pocketdeck.application.advice.ObserveAdvisorSettings
import dev.pocketdeck.application.advice.SaveAdvisorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the player edits for the AI services: the keys and models by provider, and which provider is asked first. */
data class AiSettingsForm(
    val order: List<AdvisorProvider>,
    val keys: Map<AdvisorProvider, String>,
    val models: Map<AdvisorProvider, String>,
)

/** What the model picker shows while it is open. */
sealed interface ModelPickerContent {
    data object Loading : ModelPickerContent

    data class Loaded(
        val models: List<ListedModel>,
    ) : ModelPickerContent

    data class Failed(
        val message: String?,
    ) : ModelPickerContent
}

data class ModelPicker(
    val provider: AdvisorProvider,
    val content: ModelPickerContent,
    /** Whether paid models are listed too; only OpenRouter has free ones to leave out. */
    val includePaid: Boolean = false,
) {
    /** OpenRouter models are tried in turn, so several can be chosen; a Claude model is chosen alone. */
    val chooseSeveral: Boolean get() = provider == AdvisorProvider.OPENROUTER
}

data class SettingsUiState(
    val form: AiSettingsForm = AiSettingsForm(AdvisorProvider.entries, emptyMap(), emptyMap()),
    /** Whether the form differs from what is saved. */
    val changed: Boolean = false,
    /** Whether the player has just saved, so the screen can say so until they edit again. */
    val justSaved: Boolean = false,
    /** The model picker while it is open; null when closed. */
    val modelPicker: ModelPicker? = null,
)

/** Drives the settings screen: edits are kept in a form and written only when the player saves. */
class SettingsViewModel(
    observeSettings: ObserveAdvisorSettings,
    private val saveSettings: SaveAdvisorSettings,
    private val listModels: ListSuitableModels,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                // What the player is typing is not overwritten by what was saved before.
                mutableState.update { if (it.changed) it else it.copy(form = formOf(settings)) }
            }
        }
    }

    fun onKeyChanged(
        provider: AdvisorProvider,
        key: String,
    ) = edit { it.copy(keys = it.keys + (provider to key)) }

    fun onModelChanged(
        provider: AdvisorProvider,
        model: String,
    ) = edit { it.copy(models = it.models + (provider to model)) }

    /** Asks the provider earlier (-1) or later (+1); the first cannot go earlier and the last not later. */
    fun onMove(
        provider: AdvisorProvider,
        by: Int,
    ) = edit { form ->
        val index = form.order.indexOf(provider)
        val target = index + by
        if (index < 0 || target !in form.order.indices) {
            form
        } else {
            form.copy(order = form.order.toMutableList().also { it.add(target, it.removeAt(index)) })
        }
    }

    fun onOpenModelPicker(provider: AdvisorProvider) = loadModels(ModelPicker(provider, ModelPickerContent.Loading))

    /** Lists paid models too, or not; the list is asked for again. */
    fun onIncludePaidChanged(includePaid: Boolean) {
        val picker = mutableState.value.modelPicker ?: return
        loadModels(picker.copy(content = ModelPickerContent.Loading, includePaid = includePaid))
    }

    private fun loadModels(picker: ModelPicker) {
        mutableState.update { it.copy(modelPicker = picker) }
        viewModelScope.launch {
            // The key as typed, saved or not: the player should not have to save before looking at Claude's models.
            val request =
                ModelRequest(picker.provider, mutableState.value.form.keys[picker.provider], picker.includePaid)
            val content =
                when (val outcome = listModels(request)) {
                    is Outcome.Success -> ModelPickerContent.Loaded(outcome.value)
                    is Outcome.Failure -> ModelPickerContent.Failed(outcome.reason.message())
                }
            mutableState.update { state ->
                state.modelPicker?.let { state.copy(modelPicker = it.copy(content = content)) }
                    ?: state
            }
        }
    }

    fun onCloseModelPicker() = mutableState.update { it.copy(modelPicker = null) }

    /** Takes the model in or out of the list written in the model field; the order is the order they are tried in. */
    fun onToggleModel(
        provider: AdvisorProvider,
        id: String,
    ) = edit { form ->
        val chosen =
            form.models[provider]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val several = provider == AdvisorProvider.OPENROUTER
        val updated =
            when {
                id in chosen -> chosen - id
                several -> chosen + id
                else -> listOf(id)
            }
        form.copy(
            models =
                if (updated.isEmpty()) {
                    form.models - provider
                } else {
                    form.models +
                        (provider to updated.joinToString(", "))
                },
        )
    }

    private fun AdvisorFailure.message(): String? =
        when (this) {
            is AdvisorFailure.NotReachable -> reason
            is AdvisorFailure.Rejected -> reason
            is AdvisorFailure.Unusable -> reason
            is AdvisorFailure.Busy -> reason
            AdvisorFailure.MissingKey, AdvisorFailure.InvalidKey -> null
        }

    fun onSave() {
        val form = mutableState.value.form
        viewModelScope.launch {
            val settings =
                AdvisorProvider.entries.fold(AdvisorSettings().withOrder(form.order)) { acc, provider ->
                    acc.withKey(provider, form.keys[provider]).withModel(provider, form.models[provider])
                }
            saveSettings(settings)
            mutableState.update { SettingsUiState(form = formOf(settings), changed = false, justSaved = true) }
        }
    }

    private fun edit(change: (AiSettingsForm) -> AiSettingsForm) =
        mutableState.update { it.copy(form = change(it.form), changed = true, justSaved = false) }

    private fun formOf(settings: AdvisorSettings) =
        AiSettingsForm(
            order = settings.order,
            keys = AdvisorProvider.entries.mapNotNull { p -> settings.keyOf(p)?.let { p to it } }.toMap(),
            models = AdvisorProvider.entries.mapNotNull { p -> settings.chosenModelOf(p)?.let { p to it } }.toMap(),
        )

    class Factory(
        private val observeSettings: ObserveAdvisorSettings,
        private val saveSettings: SaveAdvisorSettings,
        private val listModels: ListSuitableModels,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return SettingsViewModel(observeSettings, saveSettings, listModels) as T
        }
    }
}
