package com.splitparty.android.domain.repository

/** Outcome of a Demo reset. A failure changed nothing on this phone. */
sealed interface DemoResetResult {
    data object Done : DemoResetResult

    /**
     * The call failed. [message] is the function's own text when it sent one
     * (e.g. its "may be partially reset; tap Reset again"), else null.
     */
    data class Failed(val reason: DemoResetFailure, val message: String? = null) : DemoResetResult
}

enum class DemoResetFailure {
    /** Offline, the server could not be reached, or no answer in time. */
    Unreachable,

    /** The project has no `resetDemo` (`npm run deploy:functions` was not run). */
    NotDeployed,

    /** The function answered with an error (a partial reset among them); tapping again is safe. */
    Server
}

/**
 * The Me tab's "Demo reset": the callable Cloud Function `resetDemo` restores
 * the seeded world with admin rights (phones may not delete parties or
 * transactions). Every phone then catches up through its live listeners.
 */
interface DemoResetRepository {
    suspend fun reset(): DemoResetResult
}
