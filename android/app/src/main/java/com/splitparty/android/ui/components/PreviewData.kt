package com.splitparty.android.ui.components

import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Sample data for @Preview functions and UI tests; mirrors the backend seed. */
internal object PreviewData {
    private const val HOUR_MS = 3_600_000L
    private val now = System.currentTimeMillis()

    val maxi = User("maxi", "maxi", "Maxi", "avatar_maxi", 25_000)
    val annie = User("annie", "annie", "Annie", "avatar_annie", 18_050)
    val christophe = User("christophe", "christophe", "Christophe", "avatar_christophe", 32_075)

    val users: ImmutableMap<String, User> = persistentMapOf(maxi.id to maxi, annie.id to annie, christophe.id to christophe)

    val transactions = persistentListOf(
        Transaction("t1", "annie", "maxi", 3_115, "Tony's Split Party", "Tony's", null, now, 0, 0),
        Transaction("t2", "maxi", "annie", 900, "🍦 ice cream run", null, null, now - 48 * HOUR_MS, 1, 1),
        Transaction("t3", "annie", "christophe", 3_000, "Uber home 🚕", null, null, now - 96 * HOUR_MS, 0, 0)
    )
}
