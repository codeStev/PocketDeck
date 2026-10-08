import java.util.Properties

plugins {
    id("pocketdeck.android.application")
}

// SemVer from version.properties; versionCode is derived so that it always rises with the version:
// major*1_000_000 + minor*10_000 + patch*100 + (pre-release number, or 99 for a final release).
// So 1.0.0-rc.1 < 1.0.0-rc.2 < 1.0.0, and a patch can ship up to 98 pre-releases.
val semver = Regex("""(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z]+\.(\d+))?""")
val appVersion =
    Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }.getProperty("version")
val versionParts =
    checkNotNull(semver.matchEntire(appVersion)) { "version.properties: '$appVersion' is not MAJOR.MINOR.PATCH[-pre.N]" }
        .destructured
val appVersionCode =
    versionParts.let { (major, minor, patch, pre) ->
        major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + (pre.toIntOrNull() ?: 99)
    }

android {
    namespace = "dev.pocketdeck"
    defaultConfig {
        applicationId = "dev.pocketdeck"
        versionCode = appVersionCode
        versionName = appVersion
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
