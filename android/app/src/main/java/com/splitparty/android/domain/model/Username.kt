package com.splitparty.android.domain.model

import java.util.Locale

/** Username rules from the schema: doc id == lower-case username. */
object Username {
    private val VALID = Regex("^[a-z0-9_]{3,20}$")

    /** What login looks up: `users/{username.trim().toLowerCase()}`. */
    fun normalize(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    /** Rejecting invalid ids up front also keeps "/" out of the document path. */
    fun isValid(id: String): Boolean = VALID.matches(id)

    fun fallbackName(id: String): String = id.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
