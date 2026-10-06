package dev.pocketdeck.application.advice

import kotlinx.coroutines.flow.Flow

/** The AI services a deck can be asked from. [defaultModel] is used until the player chooses another. */
enum class AdvisorProvider(
    /** The models asked, in this order, until one gives a deck, when the player has not chosen a model. */
    val defaultModels: List<String>,
) {
    /** Claude, directly from Anthropic. Strongest, costs money. */
    ANTHROPIC(listOf("claude-opus-5-5")),

    /**
     * Many models behind one key. The defaults are free models that take tools and a very long prompt: Gemma 4 does
     * not think unless asked, and Nemotron Super is the fallback that does think (kept short). The `openrouter/free`
     * router is avoided on purpose: it hands the request to any free model, including ones that think for minutes.
     */
    OPENROUTER(
        listOf(
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
        ),
    ),
    ;

    val defaultModel: String get() = defaultModels.first()
}

/** What the player has set up for the advisors: keys, chosen models and which provider is tried first. */
data class AdvisorSettings(
    val order: List<AdvisorProvider> = AdvisorProvider.entries,
    private val keys: Map<AdvisorProvider, String> = emptyMap(),
    private val models: Map<AdvisorProvider, String> = emptyMap(),
) {
    fun keyOf(provider: AdvisorProvider): String? = keys[provider]?.takeIf { it.isNotBlank() }

    fun modelOf(provider: AdvisorProvider): String =
        models[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    /** The models to ask, in order: the one the player chose, or else the provider's defaults. */
    fun modelsToTry(provider: AdvisorProvider): List<String> =
        chosenModelOf(provider)?.let { listOf(it) } ?: provider.defaultModels

    /** The model the player typed in, if any (the default does not count). */
    fun chosenModelOf(provider: AdvisorProvider): String? = models[provider]?.takeIf { it.isNotBlank() }

    /** The providers that have a key, in the order they are tried. */
    fun usable(): List<AdvisorProvider> = order.filter { keyOf(it) != null }

    fun withKey(
        provider: AdvisorProvider,
        key: String?,
    ) = copy(keys = if (key.isNullOrBlank()) keys - provider else keys + (provider to key.trim()))

    fun withModel(
        provider: AdvisorProvider,
        model: String?,
    ) = copy(models = if (model.isNullOrBlank()) models - provider else models + (provider to model.trim()))

    fun withOrder(newOrder: List<AdvisorProvider>) =
        copy(
            order =
                newOrder + (AdvisorProvider.entries - newOrder.toSet()),
        )
}

/** Outbound port: keeps the advisor settings, including the keys, on the device. */
interface AdvisorSettingsStore {
    fun observe(): Flow<AdvisorSettings>

    suspend fun current(): AdvisorSettings

    suspend fun save(settings: AdvisorSettings)
}

/** Inbound port: follow the advisor settings. */
fun interface ObserveAdvisorSettings {
    operator fun invoke(): Flow<AdvisorSettings>
}

/** Inbound port: change the advisor settings. */
fun interface SaveAdvisorSettings {
    suspend operator fun invoke(settings: AdvisorSettings)
}

class ObserveAdvisorSettingsService(
    private val store: AdvisorSettingsStore,
) : ObserveAdvisorSettings {
    override fun invoke() = store.observe()
}

class SaveAdvisorSettingsService(
    private val store: AdvisorSettingsStore,
) : SaveAdvisorSettings {
    override suspend fun invoke(settings: AdvisorSettings) = store.save(settings)
}
