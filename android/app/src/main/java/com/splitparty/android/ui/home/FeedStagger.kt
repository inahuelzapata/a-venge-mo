package com.splitparty.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.splitparty.android.domain.model.Transaction
import kotlinx.coroutines.delay

/** As on iOS: 250 ms between two payments dropping onto the top of the feed. */
internal const val FEED_DROP_IN_MS = 250L

/**
 * Which rows of a feed delivery are news, in the order they should drop in.
 * One Pay writes every payment at once; rather than all of them popping in
 * together, the oldest lands first and each newer one lands above it. The
 * first delivery, a single new row, or motion turned off: nothing to stagger.
 */
internal class FeedStagger {
    private var known: Set<String>? = null

    /** Ids to reveal one by one, oldest first (the feed is newest first). */
    fun arrivals(rows: List<Transaction>, animate: Boolean): List<String> {
        val ids = rows.map { it.id }
        val previous = known
        known = ids.toSet()
        if (previous == null || !animate) return emptyList()
        val fresh = ids.filterNot { it in previous }
        return if (fresh.size > 1) fresh.reversed() else emptyList()
    }
}

/** [rows] with the newest arrivals held back and dropped in one at a time. */
@Composable
internal fun rememberStaggeredFeed(rows: List<Transaction>, animate: Boolean): List<Transaction> {
    val stagger = remember { FeedStagger() }
    val arrivals = remember(rows) { stagger.arrivals(rows, animate) }
    // The oldest arrival shows at once, the others follow 250 ms apart.
    var revealed by remember(rows) { mutableIntStateOf(1) }
    LaunchedEffect(arrivals) {
        while (revealed < arrivals.size) {
            delay(FEED_DROP_IN_MS)
            revealed++
        }
    }
    if (revealed >= arrivals.size) return rows
    val held = arrivals.drop(revealed).toSet()
    return rows.filterNot { it.id in held }
}
