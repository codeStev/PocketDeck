package dev.pocketdeck.adapter.persistence

import android.content.Context
import android.content.SharedPreferences
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import dev.pocketdeck.application.advice.AdvisorSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Keeps the advisor settings in the app's private storage. The keys are encrypted with a key from the Android Keystore
 * (see [KeystoreSecretBox]); the app is excluded from every backup.
 */
class SharedPreferencesAdvisorSettingsStore internal constructor(
    context: Context,
    fileName: String,
    private val secrets: SecretBox,
) : AdvisorSettingsStore {
    constructor(context: Context) : this(context, DEFAULT_FILE, KeystoreSecretBox())

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            fileName,
            Context.MODE_PRIVATE,
        )
    private val state = MutableStateFlow(read())

    init {
        upgradePlainKeys()
    }

    override fun observe(): Flow<AdvisorSettings> = state

    override suspend fun current(): AdvisorSettings = state.value

    override suspend fun save(settings: AdvisorSettings) {
        withContext(Dispatchers.IO) {
            val editor = preferences.edit().clear().putString(ORDER, settings.order.joinToString(SEPARATOR) { it.name })
            AdvisorProvider.entries.forEach { provider ->
                settings.keyOf(provider)?.let { editor.putString(KEY_PREFIX + provider.name, secrets.seal(it)) }
                settings.chosenModelOf(provider)?.let { editor.putString(MODEL_PREFIX + provider.name, it) }
                settings.addressOf(provider)?.let { editor.putString(ADDRESS_PREFIX + provider.name, it) }
            }
            editor.putBoolean(PLAIN_HTTP, settings.plainHttpAllowed)
            editor.commit()
        }
        state.value = settings
    }

    private fun read(): AdvisorSettings {
        val order =
            preferences.getString(ORDER, null)?.split(SEPARATOR)?.mapNotNull { name ->
                AdvisorProvider.entries.firstOrNull {
                    it.name ==
                        name
                }
            }
        val start =
            AdvisorSettings()
                .withOrder(
                    order.orEmpty(),
                ).withPlainHttpAllowed(preferences.getBoolean(PLAIN_HTTP, false))
        return AdvisorProvider.entries.fold(start) { settings, provider ->
            settings
                .withKey(provider, preferences.getString(KEY_PREFIX + provider.name, null)?.let(secrets::open))
                .withModel(provider, preferences.getString(MODEL_PREFIX + provider.name, null))
                .withAddress(provider, preferences.getString(ADDRESS_PREFIX + provider.name, null))
        }
    }

    /** Keys stored by an earlier version as plain text are sealed the first time the settings are read. */
    private fun upgradePlainKeys() {
        val editor = preferences.edit()
        var upgraded = false
        AdvisorProvider.entries.forEach { provider ->
            val stored = preferences.getString(KEY_PREFIX + provider.name, null) ?: return@forEach
            if (!secrets.isSealed(stored)) {
                editor.putString(KEY_PREFIX + provider.name, secrets.seal(stored))
                upgraded = true
            }
        }
        if (upgraded) editor.apply()
    }

    private companion object {
        const val DEFAULT_FILE = "advisor_settings"
        const val ORDER = "order"
        const val KEY_PREFIX = "key_"
        const val MODEL_PREFIX = "model_"
        const val ADDRESS_PREFIX = "address_"
        const val PLAIN_HTTP = "plain_http"
        const val SEPARATOR = ","
    }
}
