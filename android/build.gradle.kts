plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin); only the Compose compiler plugin is added.
    alias(libs.plugins.kotlin.compose) apply false
    // Applied by app/ only when app/google-services.json exists (it is git-ignored).
    alias(libs.plugins.google.services) apply false
}
