plugins {
    id("pocketdeck.android.compose")
}

android {
    namespace = "dev.codestev.pocketdesign"
}

dependencies {
    implementation(platform(libs.compose.bom))
    // Compose types (Modifier, Color, ImageVector) are part of the public API of the components.
    api(libs.compose.ui)
    api(libs.compose.foundation)
    // Material 3 stays underneath for text input, dialogs and accessibility; the theme maps onto it.
    implementation(libs.compose.material3)

    // The catalog screen (debug builds only) is reachable with `adb shell am start`.
    debugImplementation(libs.androidx.activity.compose)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
}
