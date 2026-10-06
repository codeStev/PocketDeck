plugins {
    id("pocketdeck.android.application")
}

android {
    namespace = "dev.pocketdeck"
    defaultConfig {
        applicationId = "dev.pocketdeck"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":adapter:ui"))
    implementation(project(":adapter:persistence"))
    implementation(project(":adapter:carddb"))
    implementation(project(":adapter:recognition"))
    implementation(project(":adapter:capture"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
