package com.splitparty.android.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** One emission of a live Firestore listener. */
sealed interface LiveEvent<out T> {
    /** Listener (re)started, nothing received yet. */
    data object Pending : LiveEvent<Nothing>

    /** [fromCache] = true means the server has not confirmed this data (offline or unreachable). */
    data class Data<T>(val value: T, val fromCache: Boolean) : LiveEvent<T>

    /** The listener failed; it is retried automatically. */
    data class Failed(val error: Throwable) : LiveEvent<Nothing>
}

class SplitPartyRepository(private val db: FirebaseFirestore) {

    /** Username-only login: the account exists if `users/{username}` exists. Asks the server, never the cache. */
    suspend fun findUser(normalizedUsername: String): User? = withTimeout(LOGIN_TIMEOUT_MS) {
        db.collection(Collections.USERS).document(normalizedUsername).get(Source.SERVER).await().toUser()
    }

    /** Feed query from the schema: `transactions orderBy(createdAt desc) limit(50)`. */
    fun feed(): Flow<LiveEvent<List<Transaction>>> = listen(
        db.collection(Collections.TRANSACTIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_LIMIT),
    )

    /** Me query from the schema: `where(participantIds array-contains uid) orderBy(createdAt desc) limit(50)`. */
    fun transactionsOf(userId: String): Flow<LiveEvent<List<Transaction>>> = listen(
        db.collection(Collections.TRANSACTIONS)
            .whereArrayContains("participantIds", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_LIMIT),
    )

    fun user(userId: String): Flow<LiveEvent<User?>> =
        listen(db.collection(Collections.USERS).document(userId))

    /** All users, for display names and avatars in feed rows. */
    fun users(): Flow<LiveEvent<Map<String, User>>> = retrying {
        callbackFlow {
            val registration = db.collection(Collections.USERS)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        trySend(LiveEvent.Failed(error)); close()
                    } else if (snapshot != null) {
                        val users = snapshot.documents.mapNotNull { it.toUser() }.associateBy { it.id }
                        trySend(LiveEvent.Data(users, snapshot.metadata.isFromCache))
                    }
                }
            awaitClose { registration.remove() }
        }
    }

    private fun listen(query: Query): Flow<LiveEvent<List<Transaction>>> = retrying {
        callbackFlow {
            val registration: ListenerRegistration =
                query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        trySend(LiveEvent.Failed(error)); close()
                    } else if (snapshot != null) {
                        trySend(LiveEvent.Data(snapshot.documents.map { it.toTransaction() }, snapshot.metadata.isFromCache))
                    }
                }
            awaitClose { registration.remove() }
        }
    }

    private fun listen(doc: DocumentReference): Flow<LiveEvent<User?>> = retrying {
        callbackFlow {
            val registration = doc.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(LiveEvent.Failed(error)); close()
                } else if (snapshot != null) {
                    trySend(LiveEvent.Data(snapshot.toUser(), snapshot.metadata.isFromCache))
                }
            }
            awaitClose { registration.remove() }
        }
    }

    /**
     * A Firestore listener that fails (for example a missing index or rules
     * error) is dead. Start a new one after a short pause so the screen
     * recovers by itself.
     */
    private fun <T> retrying(listener: () -> Flow<LiveEvent<T>>): Flow<LiveEvent<T>> = flow {
        while (true) {
            emit(LiveEvent.Pending)
            emitAll(listener())
            delay(RETRY_DELAY_MS)
        }
    }

    private companion object {
        const val FEED_LIMIT = 50L
        const val LOGIN_TIMEOUT_MS = 8_000L
        const val RETRY_DELAY_MS = 3_000L
    }
}
