package com.splitparty.android.ui.shell

import kotlinx.serialization.Serializable

/** Type-safe destinations of the signed-in shell (Navigation Compose). */
@Serializable
data object HomeDestination

@Serializable
data object PayDestination

@Serializable
data object MeDestination
