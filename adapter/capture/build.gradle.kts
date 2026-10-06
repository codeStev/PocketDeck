plugins {
    id("pocketdeck.android.library")
}

android {
    namespace = "dev.pocketdeck.adapter.capture"
}

dependencies {
    implementation(project(":core:application"))
}
