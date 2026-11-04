package com.splitparty.android.domain.party

import com.splitparty.android.domain.model.Party

/** What Home's banner slot shows (one at most). */
sealed interface BannerSlot {
    /** Demo switch off and nothing to join: the feed starts right below the header. */
    data object None : BannerSlot

    /** Tony's "Split the check with friends" card (demo switch on). */
    data object Merchant : BannerSlot

    /** A friend's open party this user may join. */
    data class Join(val party: Party) : BannerSlot

    /** The user is in an open party: always a way back to it. */
    data class BackToParty(val party: Party) : BannerSlot

    companion object {
        /**
         * Priority: the user's own active party, then the newest joinable
         * friend's party (not locked out), then the merchant card if the demo
         * switch is on. "Active" is judged by [nowMillis], not the stored status.
         */
        fun resolve(
            userId: String,
            friendIds: Collection<String>,
            parties: List<Party>,
            demoAtMerchant: Boolean,
            nowMillis: Long
        ): BannerSlot {
            val active = parties.filter { it.isActiveAt(nowMillis) }
            val mine = active.filter { it.hasMember(userId) }.maxByOrNull { it.createdAtMillis }
            val joinable = active
                .filter { it.hostId in friendIds && !it.isLockedOut(userId) && it.members.size < Party.MAX_MEMBERS }
                .maxByOrNull { it.createdAtMillis }
            return when {
                mine != null -> BackToParty(mine)
                joinable != null -> Join(joinable)
                demoAtMerchant -> Merchant
                else -> None
            }
        }
    }
}
