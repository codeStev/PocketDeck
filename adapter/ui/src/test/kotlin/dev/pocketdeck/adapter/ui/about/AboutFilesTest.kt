package dev.pocketdeck.adapter.ui.about

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/** The About screen and the files in the repository must say the same thing. */
class AboutFilesTest {
    private val root = File("../..").canonicalFile

    @Test
    fun `every credit on the screen is named in NOTICE`() {
        val notice = File(root, "NOTICE.md").readText()

        CREDITS.filterNot { it.name in notice }.map { it.name } shouldBe emptyList()
    }

    @Test
    fun `the licence text in the app is the licence file of the repository`() {
        val bundled = File("src/main/res/raw/license_agpl.txt").readText()

        bundled shouldBe File(root, "LICENSE").readText()
    }

    @Test
    fun `the links point at the repository the release notes name`() {
        Links.PRIVACY shouldBe "https://github.com/codeStev/PocketDeck/blob/main/PRIVACY.md"
        Links.ISSUES shouldBe "https://github.com/codeStev/PocketDeck/issues"
        File(root, "README.md").readText().contains(Links.REPOSITORY) shouldBe true
    }
}
