import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.paparazzi)
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

fun firestoreSetting(key: String): String = "\"" + firestoreProperties.getProperty(key, "").trim() + "\""

android {
    namespace = "com.splitparty.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.splitparty.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each on-device test runs in its own process with app data and permissions
        // cleared (Orchestrator), so the camera-permission tests always start fresh.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        buildConfigField("String", "FIREBASE_TARGET", firestoreSetting("FIREBASE_TARGET"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firestoreSetting("FIREBASE_PROJECT_ID"))
        buildConfigField("String", "FIRESTORE_EMULATOR_HOST", firestoreSetting("FIRESTORE_EMULATOR_HOST"))
        // Optional: the free Demo reset endpoint (backend/reset-endpoint) when the
        // project has no Cloud Functions; empty = the resetDemo callable.
        buildConfigField("String", "RESET_DEMO_URL", firestoreSetting("RESET_DEMO_URL"))
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
    // Fakes and test data used by both the JVM tests and the on-device (Espresso) tests.
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }
    testOptions {
        // On-device tests run with system animations off (connectedAndroidTest),
        // which is also the app's reduced-motion path.
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        // Robolectric (Compose UI tests on the JVM) needs the merged resources and manifest.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Unit tests run on the Gradle JVM (Java 25), which closes the JDK internals
            // Robolectric's native runtime reflects into.
            test.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED"
            )
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

// Shared artwork lives once in the repo's brand/ folder (iOS bundles the same
// file). It is copied into generated resources at build time, never committed
// under android/. drawable-nodpi: the 1738 px source is wider than a full-width
// card on any phone (378 dp image x 4.5), so one size stays crisp at every density.
//
// Missing file (or an unpulled Git LFS pointer instead of a PNG): the build still
// succeeds and emits a plain chip-grey vector at the artwork's 1738 x 905
// proportions under the same resource name, so the card keeps its height and its
// "Start a party" button. Run `git lfs pull` to get the real artwork.
abstract class SharedBannerResources : DefaultTask() {
    // A file collection, not @InputFile, so an absent source is a valid input.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val banner: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val source = banner.singleFile
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val isPng = source.isFile && source.inputStream().use { it.readNBytes(pngSignature.size) }.contentEquals(pngSignature)
        if (isPng) {
            val target = out.resolve("drawable-nodpi").apply { mkdirs() }
            source.copyTo(target.resolve("split_party_banner.png"))
        } else {
            logger.warn("Split the check banner not found at $source (missing or not pulled from Git LFS); using a plain placeholder.")
            val target = out.resolve("drawable").apply { mkdirs() }
            target.resolve("split_party_banner.xml").writeText(
                """
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="1738dp" android:height="905dp"
                    android:viewportWidth="1738" android:viewportHeight="905">
                    <path android:fillColor="#FFF1F1F3" android:pathData="M0,0h1738v905h-1738z" />
                </vector>
                """.trimIndent()
            )
        }
    }
}

val sharedBannerResources = tasks.register<SharedBannerResources>("sharedBannerResources") {
    banner.from(rootProject.layout.projectDirectory.file("../brand/banners/split-the-check-with-friends.png"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(sharedBannerResources, SharedBannerResources::outputDir)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
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
    implementation(libs.firebase.functions)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // On-device tests (src/androidTest): connectedDebugAndroidTest on an emulator or phone.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.koin.bom))
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(libs.junit)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)

    ktlintRuleset(libs.compose.rules.ktlint)
    detektPlugins(libs.compose.rules.detekt)
}

// Screenshot goldens (app/src/test/snapshots) are compared on every `check`.
// Plain testDebugUnitTest only renders them; re-record with ./gradlew recordPaparazziDebug.
tasks.named("check") { dependsOn("verifyPaparazziDebug") }
