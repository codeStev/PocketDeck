package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.consent.Consent
import dev.pocketdeck.application.consent.ConsentStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryConsentStore(
    granted: Set<Consent> = emptySet(),
) : ConsentStore {
    private val state = MutableStateFlow(granted)

    override fun observe(consent: Consent): Flow<Boolean> = state.map { consent in it }

    override suspend fun grant(consent: Consent) {
        state.value = state.value + consent
    }
}
