package com.splitparty.android.ui.home

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The merchant card's arrival (slide-in plus one sheen) plays once per
 * session, as on iOS, and again after a Demo reset or after the demo switch
 * is turned back on. App-wide, so switching tabs does not replay it.
 */
class MerchantCardEntrance {
    private val pending = AtomicBoolean(true)

    /** True exactly once per arrival: the caller plays it. */
    fun take(): Boolean = pending.getAndSet(false)

    /** The next time Home shows the card, it arrives again. */
    fun replay() = pending.set(true)
}
