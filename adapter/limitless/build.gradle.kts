plugins {
    id("pocketdeck.android.library")
}

android {
    namespace = "dev.pocketdeck.adapter.limitless"
}

dependencies {
    implementation(project(":core:application"))
}
