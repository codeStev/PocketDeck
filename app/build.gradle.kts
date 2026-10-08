import java.util.Properties

plugins {
    id("pocketdeck.android.application")
}

// SemVer from version.properties (the next release, e.g. 1.0.0). A build from develop passes -Ppocketdeck.rc=N and becomes
// the release candidate 1.0.0-rc.N; without it the build is the release itself. versionCode is derived so that it always rises:
// major*1_000_000 + minor*10_000 + patch*100 + (pre-release number, or 99 for a final release).
// So 1.0.0-rc.1 < 1.0.0-rc.2 < 1.0.0, and a patch can ship up to 98 pre-releases.
val semver = Regex("""(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z]+\.(\d+))?""")

fun versionCodeOf(version: String): Int {
    val (major, minor, patch, pre) =
        checkNotNull(semver.matchEntire(version)) { "'$version' is not MAJOR.MINOR.PATCH[-pre.N]" }.destructured
    return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + (pre.toIntOrNull() ?: 99)
}

val versionFile = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val baseVersion = versionFile.getProperty("version")
val appVersion = providers.gradleProperty("pocketdeck.rc").orNull?.let { "$baseVersion-rc.$it" } ?: baseVersion
val appVersionCode = versionCodeOf(appVersion)

// version.properties also states the code of the release itself, for tools that cannot run Gradle (F-Droid's update checker);
// a version bump that forgets it would publish a wrong code.
check(versionFile.getProperty("versionCode") == versionCodeOf(baseVersion).toString()) {
    "version.properties: versionCode must be ${versionCodeOf(baseVersion)} for version $baseVersion"
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
