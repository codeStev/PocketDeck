plugins {
    id("pocketdeck.kotlin.jvm")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.konsist)
}

tasks.test {
    // Konsist reads the other modules' sources from disk; declare them so edits invalidate the cache.
    // Rooted at src/main so the module build outputs are not (implicitly) consumed.
    val scannedModules =
        listOf("core", "adapter").flatMap { group ->
            rootProject.layout.projectDirectory
                .dir(group)
                .asFile
                .listFiles { file -> file.isDirectory }
                .orEmpty()
                .map { it.resolve("src/main") }
        }
    inputs
        .files(scannedModules.map { fileTree(it) { include("**/*.kt") } })
        .withPropertyName("scannedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .files(fileTree(rootProject.layout.projectDirectory.dir("designsystem/src")) { include("**/*.kt") })
        .withPropertyName("designSystemSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
