import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// google-services.json is git-ignored. When it is present the plugin turns it
// into resources (the hosted target needs it); without it the build still works
// and the emulator target runs with placeholder Firebase options.
val hasGoogleServicesJson = file("google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
}

// Firestore target: the ONE place to switch is android/firestore.properties
// (git-ignored; copy firestore.properties.example). Missing file or keys become
// empty strings, and the app shows "not configured" instead of guessing.
// Same keys as iOS and backend/.env: docs/firestore-target.md
val firestoreProperties = Properties().apply {
    val file = rootProject.file("firestore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun firestoreSetting(key: String): String =
    "\"" + firestoreProperties.getProperty(key, "").trim() + "\""

android {
    namespace = "com.splitparty.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.splitparty.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "FIREBASE_TARGET", firestoreSetting("FIREBASE_TARGET"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firestoreSetting("FIREBASE_PROJECT_ID"))
        buildConfigField("String", "FIRESTORE_EMULATOR_HOST", firestoreSetting("FIRESTORE_EMULATOR_HOST"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    // App bytecode level (what runs on the phone), independent of the Java 25
    // that runs Gradle. Built-in Kotlin's jvmTarget follows targetCompatibility.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
