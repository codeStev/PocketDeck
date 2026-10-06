package dev.pocketdeck.application.scan

import dev.pocketdeck.domain.scan.ScanSession
import kotlinx.coroutines.flow.Flow

/** Outbound port: holds the scan that is currently in progress and lets others follow it. */
interface ScanSessionStore {
    fun current(): ScanSession?

    fun save(session: ScanSession)

    fun clear()

    /** Emits the running scan whenever it changes; null while no scan is running. */
    fun observe(): Flow<ScanSession?>
}
