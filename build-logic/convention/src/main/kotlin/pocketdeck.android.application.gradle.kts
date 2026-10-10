import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("pocketdeck.quality")
}

extensions.configure<ApplicationExtension> {
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        targetSdk = 37
    }
    buildFeatures.compose = true
    // AGP adds an encrypted list of the dependencies, readable only by Google, to every APK. F-Droid flags it as a blob and it
    // would make builds differ; nobody needs it for a sideloaded app.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    lint.warningsAsErrors = true

    val signingFile = file("${System.getProperty("user.home")}/.pocketdeck/signing.properties")
    val signing = if (signingFile.isFile) Properties().apply { signingFile.inputStream().use(::load) } else null
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                when {
                    providers.gradleProperty("pocketdeck.signWithDebugKey").orNull == "true" -> signingConfigs.getByName("debug")
                    signing != null -> signingConfigs.getByName("release")
                    else -> null
                }
        }
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        allWarningsAsErrors = true
    }
}
