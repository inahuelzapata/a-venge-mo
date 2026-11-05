package com.splitparty.android.domain.repository

/** Outcome of a Demo reset. A failure changed nothing on this phone. */
sealed interface DemoResetResult {
    data object Done : DemoResetResult

    /** Offline, the function is missing (not deployed) or it returned an error. */
    data object Failed : DemoResetResult
}

/**
 * The Me tab's "Demo reset": the callable Cloud Function `resetDemo` restores
 * the seeded world with admin rights (phones may not delete parties or
 * transactions). Every phone then catches up through its live listeners.
 */
interface DemoResetRepository {
    suspend fun reset(): DemoResetResult
}
