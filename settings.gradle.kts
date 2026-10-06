pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pocketdeck"

include(
    ":core:domain",
    ":core:application",
    ":adapter:ui",
    ":adapter:capture",
    ":adapter:persistence",
    ":adapter:carddb",
    ":adapter:limitless",
    ":adapter:recognition",
    ":app",
    ":architecture-test",
)
