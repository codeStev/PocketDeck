package dev.pocketdeck.adapter.ui.about

import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The last short lines about scans and AI requests, kept in memory only, for the diagnostics a player can copy into a
 * problem report. Callers write what is safe to share (no card ids, names or collection); keys are removed here as a second
 * line of defence. It outlives the log, which a release build strips.
 */
class DiagnosticsLog(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val capacity: Int = CAPACITY,
) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun note(text: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast("${LocalTime.now(clock).format(TIME)} ${scrub(text)}")
    }

    @Synchronized
    fun lines(): List<String> = lines.toList()

    private fun scrub(text: String) = KEY_LIKE.replace(text, "[key removed]")

    private companion object {
        const val CAPACITY = 200
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        /** Anything shaped like an API key of the services we know ("sk-ant-...", "sk-or-v1-..."), and long header-like tokens. */
        val KEY_LIKE = Regex("""\bsk-[A-Za-z0-9_-]{8,}|\bBearer\s+\S+""")
    }
}

/** What the phone says about itself; the app fills it in from Android. */
data class DeviceInfo(
    val appVersion: String,
    val android: String,
    val device: String,
    val screen: String,
    val language: String,
    val autoScroll: String,
    val permissions: String,
)

/** The text a player copies into a problem report. */
object DiagnosticsReport {
    fun format(
        info: DeviceInfo,
        events: List<String>,
    ): String =
        buildString {
            appendLine("PocketDeck diagnostics")
            appendLine("Version: ${info.appVersion}")
            appendLine("Android: ${info.android}")
            appendLine("Device: ${info.device}")
            appendLine("Screen: ${info.screen}")
            appendLine("Language: ${info.language}")
            appendLine("Auto-scroll service: ${info.autoScroll}")
            appendLine("Permissions: ${info.permissions}")
            appendLine()
            appendLine("Recent events (newest last):")
            if (events.isEmpty()) appendLine("(none yet)") else events.forEach { appendLine(it) }
        }.trimEnd()
}
