package com.resurgeai.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Snapshot listener as a Flow of mapped documents; closes with the listener's error. */
internal fun <T> listen(query: Query, map: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
    val registration = query.addSnapshotListener { snap, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snap != null) trySend(map(snap.documents))
    }
    awaitClose { registration.remove() }
}
