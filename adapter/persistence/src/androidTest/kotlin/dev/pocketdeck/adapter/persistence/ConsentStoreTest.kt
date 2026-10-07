package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.application.consent.Consent
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ConsentStoreTest {
    private val fileName = "consents_test"
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
    fun hasNoConsentAtFirst() =
        runTest {
            SharedPreferencesConsentStore(context, fileName).observe(Consent.AUTO_SCROLL).first() shouldBe false
        }

    @Test
    fun remembersAConsentForTheNextStart() =
        runTest {
            val store = SharedPreferencesConsentStore(context, fileName)

            store.grant(Consent.AUTO_SCROLL)

            store.observe(Consent.AUTO_SCROLL).first() shouldBe true
            SharedPreferencesConsentStore(context, fileName).observe(Consent.AUTO_SCROLL).first() shouldBe true
        }
}
