package com.splitparty.android.data

import android.util.Log
import com.splitparty.android.domain.analytics.Analytics
import com.splitparty.android.domain.analytics.AnalyticsEvent

/** [Analytics] for the PoC: one Logcat line per event under the tag "SplitPartyAnalytics". */
class LogcatAnalytics : Analytics {
    override fun record(event: AnalyticsEvent) {
        val parameters = event.parameters.toSortedMap().entries.joinToString(" ") { "${it.key}=${it.value}" }
        Log.i(TAG, "${event.name} $parameters")
    }

    private companion object {
        const val TAG = "SplitPartyAnalytics"
    }
}
