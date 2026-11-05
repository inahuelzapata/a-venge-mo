package com.splitparty.android.ui.party

import org.junit.Assert.assertEquals
import org.junit.Test

/** A member who leaves springs out in place before their seat empties (the arrival reversed). */
class SeatRosterTest {
    private val maxi = MemberUi("maxi", "Maxi", null, isHost = true)
    private val annie = MemberUi("annie", "Annie", null, isHost = false)
    private val christophe = MemberUi("christophe", "Christophe", null, isHost = false)

    private fun List<SeatRoster.Seat>.render() = map { if (it.leaving) "${it.member.userId}↓" else it.member.userId }

    @Test
    fun aRemovedMemberStaysInPlaceUntilTheDepartureHasPlayed() {
        val roster = SeatRoster()
        roster.merge(listOf(maxi, annie, christophe))

        assertEquals(listOf("maxi", "annie↓", "christophe"), roster.merge(listOf(maxi, christophe)).render())

        roster.markGone("annie")
        assertEquals(listOf("maxi", "christophe"), roster.merge(listOf(maxi, christophe)).render())
    }

    @Test
    fun aMemberWhoRejoinsMidDepartureIsSimplyBack() {
        val roster = SeatRoster()
        roster.merge(listOf(maxi, annie))
        roster.merge(listOf(maxi))

        assertEquals(listOf("maxi", "annie"), roster.merge(listOf(maxi, annie)).render())
    }

    @Test
    fun aDepartedMemberCanLeaveAgainAfterRejoining() {
        val roster = SeatRoster()
        roster.merge(listOf(maxi, annie))
        roster.merge(listOf(maxi))
        roster.markGone("annie")
        roster.merge(listOf(maxi))
        roster.merge(listOf(maxi, annie))

        assertEquals(listOf("maxi", "annie↓"), roster.merge(listOf(maxi)).render())
    }
}
