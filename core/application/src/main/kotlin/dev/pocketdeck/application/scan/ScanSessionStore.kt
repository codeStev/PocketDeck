package dev.pocketdeck.application.scan

import dev.pocketdeck.domain.scan.ScanSession

/** Outbound port: holds the scan that is currently in progress. */
interface ScanSessionStore {
    fun current(): ScanSession?

    fun save(session: ScanSession)

    fun clear()
}
