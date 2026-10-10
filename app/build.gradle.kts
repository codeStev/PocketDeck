import java.util.Properties

plugins {
    id("pocketdeck.android.application")
}

// The version comes from version.properties and the versionCode is derived from it, see docs/BRANCHING.md.
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
        applicationId = "io.github.codestev.pocketdeck"
        versionCode = appVersionCode
        versionName = appVersion
        providers.gradleProperty("pocketdeck.idSuffix").orNull?.let {
            applicationIdSuffix = it
            manifestPlaceholders["appLabel"] = "PocketDeck RC"
            // Another background colour, so the two installs can be told apart on the home screen.
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_rc"
        }
        if (!manifestPlaceholders.containsKey("appLabel")) manifestPlaceholders["appLabel"] = "PocketDeck"
        if (!manifestPlaceholders.containsKey("appIcon")) manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
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
