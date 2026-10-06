plugins {
    id("pocketdeck.kotlin.jvm")
}

dependencies {
    implementation(project(":core:application"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:domain")))
}

tasks.test {
    // The real fingerprint index is a generated app asset; the tests exercise it rather than a toy copy.
    inputs
        .file(rootProject.layout.projectDirectory.file("app/src/main/assets/card_index.bin"))
        .withPropertyName("cardIndex")
        .withPathSensitivity(PathSensitivity.NONE)
}
