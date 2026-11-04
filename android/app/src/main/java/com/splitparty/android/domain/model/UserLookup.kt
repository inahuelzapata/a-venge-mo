package com.splitparty.android.domain.model

/** Result of asking the server whether a username exists. */
sealed interface UserLookup {
    data class Found(val user: User) : UserLookup

    data object NotFound : UserLookup

    /** No answer from the server in time, or the request failed. */
    data object Unreachable : UserLookup
}
