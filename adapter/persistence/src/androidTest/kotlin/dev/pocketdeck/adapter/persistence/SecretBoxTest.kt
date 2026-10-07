package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/** Runs against the real Android Keystore, which is what the keys are meant to be protected by. */
class SecretBoxTest {
    private val box = KeystoreSecretBox("pocketdeck_test_secrets")
    private val fileName = "advisor_settings_secret_test"
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

    @Test
    fun givesBackWhatWasSealed() {
        box.open(box.seal("sk-or-v1-abc")) shouldBe "sk-or-v1-abc"
    }

    @Test
    fun theSealedTextDoesNotContainTheSecret() {
        box.seal("sk-or-v1-abc") shouldNotContain "sk-or-v1-abc"
    }

    @Test
    fun sealsTheSameSecretDifferentlyEachTime() {
        box.seal("sk-or-v1-abc") shouldNotBe box.seal("sk-or-v1-abc")
    }

    @Test
    fun aTamperedSealIsRejected() {
        val sealed = box.seal("sk-or-v1-abc")
        val tampered = sealed.dropLast(4) + "AAAA"

        box.open(tampered) shouldBe null
    }

    @Test
    fun readsAKeyStoredBeforeEncryptionExisted() {
        box.open("sk-plain-old-key") shouldBe "sk-plain-old-key"
    }

    @Test
    fun theStoredKeyIsNotReadableInThePreferencesFile() =
        runTest {
            val store = SharedPreferencesAdvisorSettingsStore(context, fileName, box)

            store.save(AdvisorSettings().withKey(AdvisorProvider.OPENROUTER, "sk-or-v1-abc"))

            val raw =
                context
                    .getSharedPreferences(
                        fileName,
                        Context.MODE_PRIVATE,
                    ).all.values
                    .joinToString { it.toString() }
            raw shouldNotContain "sk-or-v1-abc"
            SharedPreferencesAdvisorSettingsStore(
                context,
                fileName,
                box,
            ).current().keyOf(AdvisorProvider.OPENROUTER) shouldBe
                "sk-or-v1-abc"
        }

    @Test
    fun upgradesAKeyThatWasStoredAsPlainText() =
        runTest {
            context
                .getSharedPreferences(
                    fileName,
                    Context.MODE_PRIVATE,
                ).edit()
                .putString("key_OPENROUTER", "sk-plain-old-key")
                .commit()

            val store = SharedPreferencesAdvisorSettingsStore(context, fileName, box)

            store.current().keyOf(AdvisorProvider.OPENROUTER) shouldBe "sk-plain-old-key"
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).getString("key_OPENROUTER", "") shouldNotBe
                "sk-plain-old-key"
        }
}
