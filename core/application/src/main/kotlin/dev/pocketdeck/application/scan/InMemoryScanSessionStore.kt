package dev.pocketdeck.application.scan

import dev.pocketdeck.domain.scan.ScanSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A scan lives only as long as the app process, so no persistence is needed. */
class InMemoryScanSessionStore : ScanSessionStore {
    private val session = MutableStateFlow<ScanSession?>(null)

    override fun current(): ScanSession? = session.value

    override fun save(session: ScanSession) {
        this.session.value = session
    }

    override fun clear() {
        session.value = null
    }

    override fun observe(): Flow<ScanSession?> = session
}
