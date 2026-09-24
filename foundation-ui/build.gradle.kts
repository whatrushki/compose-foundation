plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

compose.resources {
    packageOfResClass = "app.what.foundation.generated.resources"
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
            api(project(":foundation-state"))
            api(project(":foundation-preferences"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.materialKolor)
            implementation(libs.bundles.coil)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "app.what.foundation.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
