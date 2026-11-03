import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.splitparty.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "FIREBASE_TARGET", firestoreSetting("FIREBASE_TARGET"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firestoreSetting("FIREBASE_PROJECT_ID"))
        buildConfigField("String", "FIRESTORE_EMULATOR_HOST", firestoreSetting("FIRESTORE_EMULATOR_HOST"))
        // Lets the placeholder screen say when the Firebase config file was not added.
        buildConfigField("boolean", "HAS_GOOGLE_SERVICES_JSON", file("google-services.json").exists().toString())
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
    kotlinOptions {
        jvmTarget = "17"
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
    debugImplementation(libs.androidx.compose.ui.tooling)
}
