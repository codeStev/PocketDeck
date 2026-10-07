package dev.pocketdeck.application.consent

import kotlinx.coroutines.flow.Flow

/** Things the player has to agree to knowingly before the app does them. */
enum class Consent {
    /** The accessibility service swipes the card list in the game while a scan runs. */
    AUTO_SCROLL,
}

/** Outbound port: remembers what the player has agreed to. */
interface ConsentStore {
    fun observe(consent: Consent): Flow<Boolean>

    suspend fun grant(consent: Consent)
}

/** Inbound port: follow whether the player has agreed. */
fun interface ObserveConsent {
    operator fun invoke(consent: Consent): Flow<Boolean>
}

/** Inbound port: record that the player agreed. */
fun interface GrantConsent {
    suspend operator fun invoke(consent: Consent)
}

class ObserveConsentService(
    private val store: ConsentStore,
) : ObserveConsent {
    override fun invoke(consent: Consent) = store.observe(consent)
}

class GrantConsentService(
    private val store: ConsentStore,
) : GrantConsent {
    override suspend fun invoke(consent: Consent) = store.grant(consent)
}
