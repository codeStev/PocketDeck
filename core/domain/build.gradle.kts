plugins {
    id("pocketdeck.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    testFixturesApi(libs.kotest.assertions)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
}
