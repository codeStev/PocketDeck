package dev.pocketdeck.application.scan

import dev.pocketdeck.domain.scan.ScanSession
import java.util.concurrent.atomic.AtomicReference

/** A scan lives only as long as the app process, so no persistence is needed. */
class InMemoryScanSessionStore : ScanSessionStore {
    private val session = AtomicReference<ScanSession?>(null)

    override fun current(): ScanSession? = session.get()

    override fun save(session: ScanSession) = this.session.set(session)

    override fun clear() = session.set(null)
}
