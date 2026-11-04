plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin); only compiler plugins are added.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Applied by app/ only when app/google-services.json exists (it is git-ignored).
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.paparazzi) apply false
}
