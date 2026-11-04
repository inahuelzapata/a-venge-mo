package com.splitparty.android.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.splitparty.android.domain.model.LiveEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private const val RETRY_DELAY_MS = 3_000L

/** A snapshot listener on [query] as [LiveEvent]s, restarted after a failure. [query] is rebuilt on every restart. */
internal fun <T> listenQuery(query: () -> Query, map: (QuerySnapshot) -> T): Flow<LiveEvent<T>> = retrying {
    callbackFlow {
        val registration = query().addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                trySend(LiveEvent.Failed(error))
                close()
            } else if (snapshot != null) {
                trySend(LiveEvent.Data(map(snapshot), snapshot.metadata.isFromCache))
            }
        }
        awaitClose { registration.remove() }
    }
}

internal fun <T> listenDocument(doc: DocumentReference, map: (DocumentSnapshot) -> T): Flow<LiveEvent<T>> = retrying {
    callbackFlow {
        val registration = doc.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                trySend(LiveEvent.Failed(error))
                close()
            } else if (snapshot != null) {
                trySend(LiveEvent.Data(map(snapshot), snapshot.metadata.isFromCache))
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
