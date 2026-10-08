plugins {
    id("pocketdeck.kotlin.jvm")
}

dependencies {
    implementation(project(":core:application"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
}
