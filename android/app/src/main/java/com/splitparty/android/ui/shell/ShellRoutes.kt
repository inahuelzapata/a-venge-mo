package com.splitparty.android.ui.shell

import kotlinx.serialization.Serializable

/** Type-safe destinations of the signed-in shell (Navigation Compose). */
@Serializable
data object HomeDestination

@Serializable
data object PayDestination

@Serializable
data object MeDestination

/** The party screen (host and members alike), pushed over Home. */
@Serializable
data class PartyDestination(val partyId: String)

/** Code confirmation for joining [partyId], pushed over Home from the Join banner. */
@Serializable
data class JoinDestination(val partyId: String)
