package com.splitparty.android.domain.analytics

import com.splitparty.android.domain.model.PartyCancellation

/** One product analytics event: a name and flat string parameters. */
data class AnalyticsEvent(val name: String, val parameters: Map<String, String>) {
    companion object {
        /** The host's cancel committed (PRD "cancelled vs settled vs timed out"). */
        fun partyCancelled(cancellation: PartyCancellation) = AnalyticsEvent(
            "party_cancelled",
            mapOf(
                "party_id" to cancellation.partyId,
                "member_count" to cancellation.memberCount.toString(),
                "seconds_since_created" to cancellation.secondsSinceCreated.toString()
            )
        )
    }
}

/** Where analytics events go. The PoC has no analytics backend: the app logs them (LogcatAnalytics). */
fun interface Analytics {
    fun record(event: AnalyticsEvent)

    companion object {
        /** Drops every event (previews and tests that do not look at analytics). */
        val None = Analytics { }
    }
}
