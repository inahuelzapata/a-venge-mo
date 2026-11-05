package com.splitparty.android.domain.party

import kotlin.random.Random

/**
 * Join codes (docs/firestore-schema.md, "Emoji palette"): two emojis from
 * the 12-emoji palette, repeats allowed (as on iOS), so 12² = 144 codes per
 * merchant. Same order and values as
 * backend/lib/schema.js EMOJI_PALETTE and firestore.rules palette().
 */
object JoinCodes {
    const val LENGTH = 2

    /** Palette emoji → the name TalkBack reads (TalkBack's own emoji names vary by version). */
    private val names = linkedMapOf(
        "🍕" to "pizza",
        "🍔" to "burger",
        "🌮" to "taco",
        "🍣" to "sushi",
        "🍩" to "doughnut",
        "🍺" to "beer",
        "🎉" to "party popper",
        "🔥" to "fire",
        "🚀" to "rocket",
        "🎸" to "guitar",
        "🐶" to "dog",
        "🌵" to "cactus"
    )

    val PALETTE: List<String> = names.keys.toList()

    fun random(random: Random = Random.Default): List<String> = List(LENGTH) { PALETTE.random(random) }

    /** `joinCodes/{merchantId}_{e1}{e2}`. */
    fun lockId(merchantId: String, code: List<String>): String = merchantId + "_" + code.joinToString("")

    /** "pizza, beer". */
    fun spokenName(code: List<String>): String = code.joinToString(", ") { names[it] ?: it }

    fun isValid(code: List<String>): Boolean = code.size == LENGTH && code.all { it in names }

    /**
     * The four confirmation options: [real] plus three decoys from the same
     * palette, all different from [real] and from each other, in random order.
     */
    fun options(real: List<String>, random: Random = Random.Default, count: Int = 4): List<List<String>> {
        val picked = linkedSetOf(real)
        while (picked.size < count) picked += random(random)
        return picked.toList().shuffled(random)
    }
}
