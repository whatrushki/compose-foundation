plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)

    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":foundation-core"))
            implementation(compose.runtime)
            implementation(libs.bundles.ktor)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "app.what.foundation.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
