package dev.pocketdeck.adapter.ui.about

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiagnosticsTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:34:56Z"), ZoneOffset.UTC)

    @Test
    fun `lines carry the time and the newest are kept when the log is full`() {
        val log = DiagnosticsLog(clock, capacity = 2)

        log.note("one")
        log.note("two")
        log.note("three")

        log.lines() shouldBe listOf("12:34:56 two", "12:34:56 three")
    }

    @Test
    fun `anything shaped like an api key is removed`() {
        val log = DiagnosticsLog(clock)

        log.note("request with sk-ant-api03-abcdefgh1234 and sk-or-v1-0123456789abcdef and Bearer abc.def")

        log.lines().single() shouldBe "12:34:56 request with [key removed] and [key removed] and [key removed]"
    }

    @Test
    fun `the report lists the facts and the events, or says there are none`() {
        val info =
            DeviceInfo(
                "1.0.0-rc.1 (3)",
                "16 (API 36)",
                "Google Pixel",
                "1080x2340, 440 dpi",
                "de",
                "on, connected",
                "overlay granted",
            )

        DiagnosticsReport.format(info, listOf("12:00:00 Page 1: tiles=9")) shouldBe
            """
            PocketDeck diagnostics
            Version: 1.0.0-rc.1 (3)
            Android: 16 (API 36)
            Device: Google Pixel
            Screen: 1080x2340, 440 dpi
            Language: de
            Auto-scroll service: on, connected
            Permissions: overlay granted

            Recent events (newest last):
            12:00:00 Page 1: tiles=9
            """.trimIndent()
        DiagnosticsReport.format(info, emptyList()).endsWith("(none yet)") shouldBe true
    }
}
