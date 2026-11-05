package com.splitparty.android.ui.party

/**
 * The seats the member row shows: everyone in the party, in join order, plus
 * anyone who just left or was removed and is still springing out, kept at the
 * place they had. Once a departure has played ([markGone]) the seat is no
 * longer listed, so the row ends with a pulsing empty seat again.
 */
internal class SeatRoster {
    data class Seat(val member: MemberUi, val leaving: Boolean)

    private var shown: List<Seat> = emptyList()
    private val gone = mutableSetOf<String>()

    fun merge(members: List<MemberUi>): List<Seat> {
        val present = members.mapTo(HashSet()) { it.userId }
        // Someone who rejoins mid-departure is simply back.
        gone.removeAll(present)
        val seats = members.mapTo(ArrayList()) { Seat(it, leaving = false) }
        shown.forEachIndexed { index, seat ->
            val id = seat.member.userId
            if (id !in present && id !in gone) seats.add(index.coerceAtMost(seats.size), Seat(seat.member, leaving = true))
        }
        shown = seats
        return seats
    }

    fun markGone(userId: String) {
        gone += userId
    }
}
