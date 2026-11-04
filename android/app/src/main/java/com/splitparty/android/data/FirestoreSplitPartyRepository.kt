package com.splitparty.android.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.splitparty.android.data.firebase.FirestoreSchema
import com.splitparty.android.data.firebase.toTransaction
import com.splitparty.android.data.firebase.toUser
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.UserLookup
import com.splitparty.android.domain.repository.SplitPartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** [SplitPartyRepository] on Firestore snapshot listeners, using the queries from docs/firestore-schema.md. */
class FirestoreSplitPartyRepository(private val db: FirebaseFirestore) : SplitPartyRepository {

    override suspend fun findUser(normalizedUsername: String): UserLookup {
        val snapshot = try {
            withTimeoutOrNull(LOGIN_TIMEOUT_MS) {
                db.collection(FirestoreSchema.USERS).document(normalizedUsername).get(Source.SERVER).await()
            }
        } catch (_: FirebaseFirestoreException) {
            null // Source.SERVER fails instead of falling back to the cache when offline.
        }
        return when {
            snapshot == null -> UserLookup.Unreachable
            else -> snapshot.toUser()?.let(UserLookup::Found) ?: UserLookup.NotFound
        }
    }

    /** Feed query: `transactions orderBy(createdAt desc) limit(50)`. */
    override fun feed(): Flow<LiveEvent<List<Transaction>>> = listen(
        db.collection(FirestoreSchema.TRANSACTIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_LIMIT)
    ) { snapshot -> snapshot.documents.map { it.toTransaction() } }

    /** Me query: `where(participantIds array-contains uid) orderBy(createdAt desc) limit(50)`. */
    override fun transactionsOf(userId: String): Flow<LiveEvent<List<Transaction>>> = listen(
        db.collection(FirestoreSchema.TRANSACTIONS)
            .whereArrayContains("participantIds", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_LIMIT)
    ) { snapshot -> snapshot.documents.map { it.toTransaction() } }

    override fun user(userId: String): Flow<LiveEvent<User?>> {
        val doc = db.collection(FirestoreSchema.USERS).document(userId)
        return listen(doc) { it.toUser() }
    }

    override fun users(): Flow<LiveEvent<Map<String, User>>> = listen(db.collection(FirestoreSchema.USERS)) { snapshot ->
        snapshot.documents.mapNotNull { it.toUser() }.associateBy { it.id }
    }

    private fun <T> listen(query: Query, map: (QuerySnapshot) -> T): Flow<LiveEvent<T>> = listenQuery({ query }, map)

    private fun <T> listen(doc: DocumentReference, map: (DocumentSnapshot) -> T): Flow<LiveEvent<T>> = listenDocument(doc, map)

    private companion object {
        const val FEED_LIMIT = 50L
        const val LOGIN_TIMEOUT_MS = 8_000L
    }
}
