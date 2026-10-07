package dev.pocketdeck.adapter.ui.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppLanguageChoiceTest {
    @Test
    fun `no language tag means the language of the phone`() {
        AppLanguageChoice.of(null) shouldBe AppLanguageChoice.SYSTEM
        AppLanguageChoice.of("") shouldBe AppLanguageChoice.SYSTEM
    }

    @Test
    fun `knows the languages the app is translated into`() {
        AppLanguageChoice.of("en") shouldBe AppLanguageChoice.ENGLISH
        AppLanguageChoice.of("de") shouldBe AppLanguageChoice.GERMAN
        AppLanguageChoice.of("DE") shouldBe AppLanguageChoice.GERMAN
    }

    @Test
    fun `a language the app does not have falls back to the phone's`() {
        AppLanguageChoice.of("fr") shouldBe AppLanguageChoice.SYSTEM
    }

    @Test
    fun `every choice but the phone's has a tag the app's locale list also declares`() {
        AppLanguageChoice.entries.mapNotNull { it.tag } shouldBe listOf("en", "de")
    }
}
