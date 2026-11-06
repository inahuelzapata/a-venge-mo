package com.splitparty.android.ui.party

/** What the host's controls in the total area ask for. */
interface TotalActions {
    fun scan()

    fun enterTotal()

    fun selectTip(choice: TipChoice)

    fun customTip()

    /** Host with "Custom shares" on: open the keypad for [userId]'s amount. */
    fun editShare(userId: String) = Unit

    /** Host: remove a member (already confirmed). */
    fun removeMember(userId: String) = Unit

    /** Member: leave the party (already confirmed). */
    fun leave() = Unit

    /** Host: settle everyone (no confirmation: joining was consent). */
    fun pay() = Unit

    /** Host: cancel the party for everyone (already confirmed on "Cancel party?"). */
    fun cancel() = Unit

    companion object {
        val None = object : TotalActions {
            override fun scan() = Unit

            override fun enterTotal() = Unit

            override fun selectTip(choice: TipChoice) = Unit

            override fun customTip() = Unit
        }
    }
}
