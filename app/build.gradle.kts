import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.zbowling.lightdeck"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zbowling.lightdeck"
        // Quest 3/3S and Meta VR Glasses run Horizon OS (Android 12L+ based).
        minSdk = 32
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Meta VR Glasses only run 64-bit code. The app has no native code today;
        // this keeps any future native dependency from shipping 32-bit only.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Resolved from the included ha-client build (see settings.gradle.kts).
    implementation("io.github.zbowling.lightdeck:ha-client")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Meta VR UI Set: Horizon OS typography, colors, icons and components with
    // Look and Pinch-ready targets and hover shapes. Replaces Material 3.
    implementation(platform(libs.metavrx.bom))
    implementation(libs.metavrx.uiset.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
