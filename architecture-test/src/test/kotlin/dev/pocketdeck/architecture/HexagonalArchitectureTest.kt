package dev.pocketdeck.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Enforces the hexagonal dependency rule: domain <- application <- adapters <- app.
 * Gradle module boundaries already prevent most violations at compile time; these tests also
 * catch forbidden framework imports and cross-adapter references that the compiler would allow.
 */
class HexagonalArchitectureTest {
    private val frameworkPrefixes =
        listOf("android.", "androidx.", "dagger.", "javax.inject.", "io.ktor.", "org.opencv.", "com.google.")

    @Test
    fun `domain does not depend on the application layer or on any framework`() {
        Konsist
            .scopeFromDirectory("core/domain/src/main")
            .files
            .assertFalse(additionalMessage = "domain must be framework-free and independent of application") { file ->
                file.imports.any { import ->
                    import.name.startsWith("dev.pocketdeck.application") ||
                        frameworkPrefixes.any(import.name::startsWith)
                }
            }
    }

    @Test
    fun `application layer does not depend on adapters or on any framework`() {
        Konsist
            .scopeFromDirectory("core/application/src/main")
            .files
            .assertFalse(additionalMessage = "application must only know domain and ports") { file ->
                file.imports.any { import ->
                    import.name.startsWith("dev.pocketdeck.adapter") ||
                        frameworkPrefixes.any(import.name::startsWith)
                }
            }
    }

    @Test
    fun `adapters never depend on other adapters`() {
        val adapters = listOf("ui", "capture", "persistence", "carddb", "limitless", "recognition")
        adapters.forEach { adapter ->
            val others = adapters - adapter
            Konsist
                .scopeFromDirectory("adapter/$adapter/src/main")
                .files
                .assertFalse(additionalMessage = "adapter '$adapter' must not import another adapter") { file ->
                    file.imports.any { import ->
                        others.any { import.name.startsWith("dev.pocketdeck.adapter.$it") }
                    }
                }
        }
    }

    @Test
    fun `no class is named Manager, Helper or Util`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .assertFalse(
                additionalMessage = "name classes after their responsibility, not Manager/Helper/Util",
            ) { clazz ->
                listOf("Manager", "Helper", "Util", "Utils").any(clazz.name::endsWith)
            }
    }
}
