package app.what.foundation.utils

enum class PlatformType {
    Android, Jvm, Ios, Wasm;

    val isWeb: Boolean get() = this == Wasm
    val isDesktop: Boolean get() = this == Jvm
    val isMobile: Boolean get() = this == Android || this == Ios
}

expect val currentPlatform: PlatformType
