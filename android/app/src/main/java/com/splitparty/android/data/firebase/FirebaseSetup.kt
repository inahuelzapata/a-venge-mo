package com.splitparty.android.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings

/** Result of connecting to the configured Firestore (docs/firestore-target.md). */
sealed interface FirebaseConnection {
    val target: FirestoreTarget

    data class Ready(override val target: FirestoreTarget, val db: FirebaseFirestore) : FirebaseConnection

    /** Something in the local setup is missing; [problems] says what to fix. */
    data class SetupRequired(override val target: FirestoreTarget, val problems: List<String>) : FirebaseConnection
}

object FirebaseSetup {
    private const val EMULATOR_APP_NAME = "split-party-emulator"
    private const val SETUP_HINT = "See android/README.md."

    fun connect(context: Context, target: FirestoreTarget = FirestoreTarget.fromBuildConfig()): FirebaseConnection = when (target) {
        is FirestoreTarget.NotConfigured -> FirebaseConnection.SetupRequired(target, listOf(target.reason))
        is FirestoreTarget.Emulator -> connectEmulator(context, target)
        is FirestoreTarget.Hosted -> connectHosted(context, target)
    }

    /**
     * The emulator keeps one database per project id, so the app must use
     * FIREBASE_PROJECT_ID (the id the seed script uses), not whatever project
     * google-services.json belongs to. A named FirebaseApp with the project id
     * forced does that, and works without google-services.json at all: the
     * emulator ignores the API key and app id.
     */
    private fun connectEmulator(context: Context, target: FirestoreTarget.Emulator): FirebaseConnection {
        val hostPort = parseHostPort(target.host)
            ?: return FirebaseConnection.SetupRequired(
                target,
                listOf("FIRESTORE_EMULATOR_HOST=\"${target.host}\" must look like 192.168.1.20:8080.")
            )
        val base = FirebaseOptions.fromResource(context) ?: FirebaseOptions.Builder()
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("emulator-placeholder-api-key")
            .build()
        val options = FirebaseOptions.Builder(base).setProjectId(target.projectId).build()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
            ?: FirebaseApp.initializeApp(context, options, EMULATOR_APP_NAME)
        val db = FirebaseFirestore.getInstance(app)
        db.useEmulator(hostPort.first, hostPort.second)
        db.useMemoryCache()
        return FirebaseConnection.Ready(target, db)
    }

    private fun connectHosted(context: Context, target: FirestoreTarget.Hosted): FirebaseConnection {
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context)
        val configProject = app?.options?.projectId
        return when {
            app == null -> FirebaseConnection.SetupRequired(
                target,
                listOf("google-services.json missing: the hosted target needs it. $SETUP_HINT")
            )

            configProject != target.projectId -> FirebaseConnection.SetupRequired(
                target,
                listOf(
                    "google-services.json is for project \"$configProject\" but FIREBASE_PROJECT_ID is " +
                        "\"${target.projectId}\". Use the config file from the same project."
                )
            )

            else -> FirebaseConnection.Ready(target, FirebaseFirestore.getInstance(app).apply { useMemoryCache() })
        }
    }

    /**
     * Memory cache only: a relaunch never shows stale data from disk, and the
     * feed reflects what the server has.
     */
    private fun FirebaseFirestore.useMemoryCache() {
        firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
    }

    internal fun parseHostPort(value: String): Pair<String, Int>? {
        val colon = value.lastIndexOf(':')
        val port = value.substring(colon + 1).toIntOrNull()
        return if (colon > 0 && port != null && port in VALID_PORTS) value.substring(0, colon) to port else null
    }

    private val VALID_PORTS = 1..65_535
}
