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
 * Keeps the advisor settings in the app's private storage. The keys sit there in plain text, which only the app (and
 * someone with root access to the phone) can read; the app is excluded from every backup.
 */
class SharedPreferencesAdvisorSettingsStore(
    context: Context,
    fileName: String = DEFAULT_FILE,
) : AdvisorSettingsStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            fileName,
            Context.MODE_PRIVATE,
        )
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<AdvisorSettings> = state

    override suspend fun current(): AdvisorSettings = state.value

    override suspend fun save(settings: AdvisorSettings) {
        withContext(Dispatchers.IO) {
            val editor = preferences.edit().clear().putString(ORDER, settings.order.joinToString(SEPARATOR) { it.name })
            AdvisorProvider.entries.forEach { provider ->
                settings.keyOf(provider)?.let { editor.putString(KEY_PREFIX + provider.name, it) }
                settings.chosenModelOf(provider)?.let { editor.putString(MODEL_PREFIX + provider.name, it) }
            }
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
        return AdvisorProvider.entries.fold(AdvisorSettings().withOrder(order.orEmpty())) { settings, provider ->
            settings
                .withKey(provider, preferences.getString(KEY_PREFIX + provider.name, null))
                .withModel(provider, preferences.getString(MODEL_PREFIX + provider.name, null))
        }
    }

    private companion object {
        const val DEFAULT_FILE = "advisor_settings"
        const val ORDER = "order"
        const val KEY_PREFIX = "key_"
        const val MODEL_PREFIX = "model_"
        const val SEPARATOR = ","
    }
}
