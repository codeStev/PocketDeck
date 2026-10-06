plugins {
    id("pocketdeck.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(testFixtures(project(":core:domain")))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(project(":core:domain")))
}
