package com.splitparty.android.domain.party

/** Naming rules for the Pay transaction (docs/firestore-schema.md, "Paying a party"), shared with iOS. */
object PartySettlement {
    /** The rules cap `transactions.memo` at 80 characters. */
    const val MAX_MEMO_LENGTH = 80

    private const val SUFFIX = " Split Party"

    /** `"{merchant.name} Split Party"`, e.g. "Tony's Split Party"; a long merchant name is cut so the memo fits. */
    fun memo(merchantName: String): String = merchantName.trim().take(MAX_MEMO_LENGTH - SUFFIX.length) + SUFFIX

    /** Deterministic id, so a retried Pay overwrites nothing new and never charges twice. */
    fun transactionId(partyId: String, payerId: String): String = "${partyId}_$payerId"
}
