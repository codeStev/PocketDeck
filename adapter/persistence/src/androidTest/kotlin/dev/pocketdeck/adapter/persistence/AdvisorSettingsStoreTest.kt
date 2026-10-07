package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AdvisorSettingsStoreTest {
    private val fileName = "advisor_settings_test"
    private lateinit var context: Context

    @Before
    fun startEmpty() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun store() =
        SharedPreferencesAdvisorSettingsStore(context, fileName, KeystoreSecretBox("pocketdeck_test_secrets"))

    @Test
    fun startsWithoutKeys() =
        runTest {
            store().current().usable() shouldBe emptyList()
        }

    @Test
    fun keepsKeysModelsAndOrderForTheNextStart() =
        runTest {
            val settings =
                AdvisorSettings()
                    .withKey(AdvisorProvider.OPENROUTER, "sk-or-1")
                    .withModel(AdvisorProvider.OPENROUTER, "google/gemma-4-31b-it:free")
                    .withKey(AdvisorProvider.ANTHROPIC, "sk-ant-1")
                    .withOrder(listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC))
            store().save(settings)

            val again = store().current()

            again.keyOf(AdvisorProvider.OPENROUTER) shouldBe "sk-or-1"
            again.modelOf(AdvisorProvider.OPENROUTER) shouldBe "google/gemma-4-31b-it:free"
            again.modelOf(AdvisorProvider.ANTHROPIC) shouldBe AdvisorProvider.ANTHROPIC.defaultModel
            again.order shouldBe listOf(AdvisorProvider.OPENROUTER, AdvisorProvider.ANTHROPIC)
        }

    @Test
    fun forgetsAKeyThatWasRemoved() =
        runTest {
            val store = store()
            store.save(AdvisorSettings().withKey(AdvisorProvider.ANTHROPIC, "sk-ant-1"))

            store.save(AdvisorSettings())

            store().current().keyOf(AdvisorProvider.ANTHROPIC) shouldBe null
        }
}
