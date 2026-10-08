package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import dev.pocketdeck.application.consent.Consent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * What the app has written into its SharedPreferences since 1.0, spelled out literally. A later version must still read
 * these files (a renamed key would silently log a player out or forget their account), so each change of a stored
 * format adds a fixture here next to the code that reads the new one.
 */
class StoredFormatsTest {
    private lateinit var context: Context

    private fun file(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before
    fun startEmpty() {
        context = ApplicationProvider.getApplicationContext()
        listOf(CONSENTS, ADVISOR, PROFILES).forEach { file(it).edit().clear().commit() }
    }

    @Test
    fun readsTheConsentsOfVersion1() =
        runTest {
            file(
                CONSENTS,
            ).edit().putBoolean("AUTO_SCROLL", true).putBoolean("WELCOME", true).putBoolean("RETIRED", true).commit()

            val store = SharedPreferencesConsentStore(context, CONSENTS)

            store.observe(Consent.AUTO_SCROLL).first() shouldBe true
            store.observe(Consent.WELCOME).first() shouldBe true
        }

    @Test
    fun aMissingConsentIsNotGiven() =
        runTest {
            file(CONSENTS).edit().putBoolean("AUTO_SCROLL", true).commit()

            SharedPreferencesConsentStore(context, CONSENTS).observe(Consent.WELCOME).first() shouldBe false
        }

    @Test
    fun readsTheActiveAccountOfVersion1() {
        file(PROFILES).edit().putLong("active_profile", 7L).commit()

        SharedPreferencesActiveProfileMemory(context, PROFILES).chosen.value shouldBe 7L
    }

    @Test
    fun noActiveAccountIsStoredAsNothing() {
        SharedPreferencesActiveProfileMemory(context, PROFILES).chosen.value shouldBe null
    }

    @Test
    fun readsTheAiSettingsOfVersion1IncludingAKeyStoredAsPlainText() =
        runTest {
            file(ADVISOR)
                .edit()
                .putString("order", "OPENROUTER,GEMINI,ANTHROPIC")
                .putString("model_OPENROUTER", "nvidia/nemotron-3-super-120b-a12b:free, google/gemma:free")
                .putString("model_ANTHROPIC", "claude-opus-5-5")
                .putString("key_ANTHROPIC", "sk-ant-from-an-early-version")
                .commit()

            val settings =
                SharedPreferencesAdvisorSettingsStore(
                    context,
                    ADVISOR,
                    KeystoreSecretBox("pocketdeck_test_secrets"),
                ).current()

            settings.order shouldBe listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC)
            settings.chosenModelOf(AdvisorProvider.OPENROUTER) shouldBe
                "nvidia/nemotron-3-super-120b-a12b:free, google/gemma:free"
            settings.chosenModelOf(AdvisorProvider.ANTHROPIC) shouldBe "claude-opus-5-5"
            settings.keyOf(AdvisorProvider.ANTHROPIC) shouldBe "sk-ant-from-an-early-version"
            file(ADVISOR).getString("key_ANTHROPIC", null) shouldNotBe "sk-ant-from-an-early-version"
        }

    @Test
    fun savesTheAiSettingsUnderTheNamesVersion1Reads() =
        runTest {
            val store =
                SharedPreferencesAdvisorSettingsStore(context, ADVISOR, KeystoreSecretBox("pocketdeck_test_secrets"))

            store.save(
                AdvisorSettings()
                    .withOrder(listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC))
                    .withModel(AdvisorProvider.ANTHROPIC, "claude-opus-5-5")
                    .withKey(AdvisorProvider.ANTHROPIC, "sk-ant-1"),
            )

            file(ADVISOR).getString("order", null) shouldBe "OPENROUTER,ANTHROPIC"
            file(ADVISOR).getString("model_ANTHROPIC", null) shouldBe "claude-opus-5-5"
            (file(ADVISOR).getString("key_ANTHROPIC", null) ?: "") shouldNotBe "sk-ant-1"
        }

    private companion object {
        // The names of the files the app uses; the tests use their own copies of them.
        const val CONSENTS = "consents_formats_test"
        const val ADVISOR = "advisor_settings_formats_test"
        const val PROFILES = "profiles_formats_test"
    }
}
