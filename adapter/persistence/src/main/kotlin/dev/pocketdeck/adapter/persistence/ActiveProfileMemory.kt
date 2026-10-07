package dev.pocketdeck.adapter.persistence

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Remembers which profile the player works with. Null until the player has chosen one. */
internal interface ActiveProfileMemory {
    val chosen: StateFlow<Long?>

    suspend fun choose(id: Long)
}

/** Keeps the choice in the app's private storage, so it survives restarts. */
internal class SharedPreferencesActiveProfileMemory(
    context: Context,
    fileName: String = DEFAULT_FILE,
) : ActiveProfileMemory {
    private val preferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getLong(KEY, NONE).takeIf { it != NONE })

    override val chosen: StateFlow<Long?> = state

    override suspend fun choose(id: Long) {
        withContext(Dispatchers.IO) { preferences.edit().putLong(KEY, id).commit() }
        state.value = id
    }

    private companion object {
        const val DEFAULT_FILE = "profiles"
        const val KEY = "active_profile"
        const val NONE = -1L
    }
}

internal class InMemoryActiveProfileMemory(
    initial: Long? = null,
) : ActiveProfileMemory {
    private val state = MutableStateFlow(initial)

    override val chosen: StateFlow<Long?> = state

    override suspend fun choose(id: Long) {
        state.value = id
    }
}
