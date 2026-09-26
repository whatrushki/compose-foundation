plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    `maven-publish`
}

allprojects {
    group = "app.what.foundation"
    version = "1.0.1"
}

subprojects {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/whatrushki/compose-foundation")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user")?.toString() ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key")?.toString() ?: ""
                }
            }
        }
    }
}
