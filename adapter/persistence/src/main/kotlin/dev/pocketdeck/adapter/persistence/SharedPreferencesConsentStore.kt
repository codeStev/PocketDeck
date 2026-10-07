package dev.pocketdeck.adapter.persistence

import android.content.Context
import android.content.SharedPreferences
import dev.pocketdeck.application.consent.Consent
import dev.pocketdeck.application.consent.ConsentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Remembers in the app's private storage what the player has agreed to. */
class SharedPreferencesConsentStore(
    context: Context,
    fileName: String = DEFAULT_FILE,
) : ConsentStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            fileName,
            Context.MODE_PRIVATE,
        )
    private val granted = MutableStateFlow(Consent.entries.filter { preferences.getBoolean(it.name, false) }.toSet())

    override fun observe(consent: Consent): Flow<Boolean> = granted.map { consent in it }

    override suspend fun grant(consent: Consent) {
        withContext(Dispatchers.IO) { preferences.edit().putBoolean(consent.name, true).commit() }
        granted.value = granted.value + consent
    }

    private companion object {
        const val DEFAULT_FILE = "consents"
    }
}
