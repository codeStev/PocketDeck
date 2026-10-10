package dev.pocketdeck.application.advice

import kotlinx.coroutines.flow.Flow

/** The AI services a deck can be asked from. [defaultModel] is used until the player chooses another. */
enum class AdvisorProvider(
    /** The models asked, in this order, until one gives a deck, when the player has not chosen a model. */
    val defaultModels: List<String>,
    /** Whether the player chooses the server's address (a runtime of their own) instead of the service having one. */
    val hasOwnAddress: Boolean = false,
) {
    /** Claude, directly from Anthropic. Strongest, costs money. */
    ANTHROPIC(listOf("claude-opus-5-5")),

    /**
     * Many models behind one key. The default is a free model that takes tools and a very long prompt and answered in
     * practice (Nemotron Super; it thinks, so the request keeps that short). The `openrouter/free` router is avoided on
     * purpose: it hands the request to any free model, including ones that think for minutes. Gemma 4 was tried and
     * dropped: its free capacity answered with HTTP 429 every time.
     */
    OPENROUTER(listOf("nvidia/nemotron-3-super-120b-a12b:free")),

    /** OpenAI itself. Its models are reasoning models, so the request keeps the thinking short. */
    OPENAI(listOf("gpt-5-mini")),

    /**
     * A server of the player's own that speaks the OpenAI chat API (Ollama, LM Studio, llama.cpp, vLLM ...), on their
     * computer or phone. Which models it has is up to them, so there is no default; the key is optional.
     */
    OWN_SERVER(emptyList(), hasOwnAddress = true),
    ;

    /** Empty when the provider has no default model, which is the case for the player's own server. */
    val defaultModel: String get() = defaultModels.firstOrNull().orEmpty()

    /** Claude is chosen alone; every other provider tries the models the player lists one after the other. */
    val takesSeveralModels: Boolean get() = this != ANTHROPIC

    /** Whether the service's model list is offered as it comes, without the free/tools/context filters OpenRouter needs. */
    val listsEveryModel: Boolean get() = this != OPENROUTER

    /** Whether it works without a key (a server on the player's own network often has none). */
    val keyIsOptional: Boolean get() = hasOwnAddress
}

/** What the player has set up for the advisors: keys, chosen models and which provider is tried first. */
data class AdvisorSettings(
    val order: List<AdvisorProvider> = AdvisorProvider.entries,
    private val keys: Map<AdvisorProvider, String> = emptyMap(),
    private val models: Map<AdvisorProvider, String> = emptyMap(),
    private val addresses: Map<AdvisorProvider, String> = emptyMap(),
    /** Whether an address that starts with http:// (no encryption) may be used; off until the player switches it on. */
    val plainHttpAllowed: Boolean = false,
) {
    fun keyOf(provider: AdvisorProvider): String? = keys[provider]?.takeIf { it.isNotBlank() }

    fun modelOf(provider: AdvisorProvider): String = modelsToTry(provider).first()

    /**
     * The models to ask, in order: those the player wrote (several may be separated by commas), or else the provider's
     * defaults.
     */
    fun modelsToTry(provider: AdvisorProvider): List<String> =
        chosenModelOf(provider)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: provider.defaultModels

    /** The model the player typed in, if any (the default does not count). */
    fun chosenModelOf(provider: AdvisorProvider): String? = models[provider]?.takeIf { it.isNotBlank() }

    /** The address the player gave for a provider of their own, without a trailing slash. */
    fun addressOf(provider: AdvisorProvider): String? =
        addresses[provider]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    /**
     * The providers that can be asked, in the order they are tried: those with a key, and the player's own server once it
     * has an address and a model (it has no default model, and needs no key).
     */
    fun usable(): List<AdvisorProvider> = order.filter { isUsable(it) }

    private fun isUsable(provider: AdvisorProvider) =
        if (provider.hasOwnAddress) {
            addressOf(provider) != null && chosenModelOf(provider) != null
        } else {
            keyOf(provider) !=
                null
        }

    fun withKey(
        provider: AdvisorProvider,
        key: String?,
    ) = copy(keys = if (key.isNullOrBlank()) keys - provider else keys + (provider to key.trim()))

    fun withModel(
        provider: AdvisorProvider,
        model: String?,
    ) = copy(models = if (model.isNullOrBlank()) models - provider else models + (provider to model.trim()))

    fun withAddress(
        provider: AdvisorProvider,
        address: String?,
    ) = copy(
        addresses = if (address.isNullOrBlank()) addresses - provider else addresses + (provider to address.trim()),
    )

    fun withPlainHttpAllowed(allowed: Boolean) = copy(plainHttpAllowed = allowed)

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
