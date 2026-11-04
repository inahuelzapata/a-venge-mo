package com.splitparty.android.data.firebase

import com.splitparty.android.BuildConfig

/**
 * Which Firestore the app talks to: the hosted project or the emulator started
 * from backend/. Values come from BuildConfig, filled from the git-ignored
 * android/firestore.properties. The same keys are used by iOS and the backend
 * scripts (docs/firestore-target.md).
 */
sealed interface FirestoreTarget {
    data class Emulator(val projectId: String, val host: String) : FirestoreTarget
    data class Hosted(val projectId: String) : FirestoreTarget

    /** The local config is missing or incomplete; [reason] says what to fix. */
    data class NotConfigured(val reason: String) : FirestoreTarget

    val summary: String
        get() = when (this) {
            is Emulator -> "Emulator $host (project $projectId)"
            is Hosted -> "Hosted project $projectId"
            is NotConfigured -> "Firestore target not configured"
        }

    companion object {
        private const val SETUP_HINT = "Copy android/firestore.properties.example to firestore.properties and rebuild."

        fun fromBuildConfig(): FirestoreTarget = resolve(
            target = BuildConfig.FIREBASE_TARGET,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            emulatorHost = BuildConfig.FIRESTORE_EMULATOR_HOST
        )

        fun resolve(target: String, projectId: String, emulatorHost: String): FirestoreTarget = when {
            target.isEmpty() -> NotConfigured("FIREBASE_TARGET is not set. $SETUP_HINT")

            projectId.isEmpty() -> NotConfigured("FIREBASE_PROJECT_ID is not set. $SETUP_HINT")

            target == "emulator" && emulatorHost.isEmpty() ->
                NotConfigured("FIRESTORE_EMULATOR_HOST is not set (use <laptop LAN IP>:8080).")

            target == "emulator" -> Emulator(projectId, emulatorHost)

            target == "hosted" -> Hosted(projectId)

            else -> NotConfigured("FIREBASE_TARGET=\"$target\" is invalid (use emulator or hosted).")
        }
    }
}
