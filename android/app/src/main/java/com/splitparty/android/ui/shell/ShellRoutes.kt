package com.splitparty.android.ui.shell

import kotlinx.serialization.Serializable

/** Type-safe destinations of the signed-in shell (Navigation Compose). */
@Serializable
data object HomeDestination

@Serializable
data object PayDestination

@Serializable
data object MeDestination

/**
 * Pushed over Home by the "Start a party" card. Today it shows the in-progress
 * placeholder; the real party screen replaces the placeholder at this route.
 */
@Serializable
data object SplitPartyDestination
