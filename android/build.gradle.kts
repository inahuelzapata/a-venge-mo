plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied by app/ only when app/google-services.json exists (it is git-ignored).
    alias(libs.plugins.google.services) apply false
}
