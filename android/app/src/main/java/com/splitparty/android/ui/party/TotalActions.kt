package com.splitparty.android.ui.party

/** What the host's controls in the total area ask for. */
interface TotalActions {
    fun scan()

    fun enterTotal()

    fun selectTip(choice: TipChoice)

    fun customTip()

    /** Host: remove a member (already confirmed). */
    fun removeMember(userId: String) = Unit

    /** Member: leave the party (already confirmed). */
    fun leave() = Unit

    /** Host: settle everyone (no confirmation: joining was consent). */
    fun pay() = Unit

    companion object {
        val None = object : TotalActions {
            override fun scan() = Unit

            override fun enterTotal() = Unit

            override fun selectTip(choice: TipChoice) = Unit

            override fun customTip() = Unit
        }
    }
}
