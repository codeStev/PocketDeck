plugins {
    id("pocketdeck.kotlin.jvm")
    id("pocketdeck.serialization")
}

dependencies {
    implementation(project(":core:application"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(testFixtures(project(":core:domain")))
}
