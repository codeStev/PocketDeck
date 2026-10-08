plugins {
    id("pocketdeck.android.application")
}

android {
    namespace = "dev.pocketdeck"
    defaultConfig {
        applicationId = "dev.pocketdeck"
        versionCode = 2
        versionName = "0.2.0"
        // A test copy of a release build can sit next to the real app: -Ppocketdeck.idSuffix=.rc
        providers.gradleProperty("pocketdeck.idSuffix").orNull?.let {
            applicationIdSuffix = it
            manifestPlaceholders["appLabel"] = "PocketDeck RC"
        }
        if (!manifestPlaceholders.containsKey("appLabel")) manifestPlaceholders["appLabel"] = "PocketDeck"
    }
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":adapter:ui"))
    implementation(project(":adapter:persistence"))
    implementation(project(":adapter:carddb"))
    implementation(project(":adapter:limitless"))
    implementation(project(":adapter:exchange"))
    implementation(project(":adapter:llm"))
    implementation(project(":adapter:recognition"))
    implementation(project(":adapter:capture"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
