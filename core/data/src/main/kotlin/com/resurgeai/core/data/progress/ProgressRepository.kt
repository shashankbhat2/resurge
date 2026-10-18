package com.resurgeai.core.data.progress

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.resurgeai.core.data.listen
import com.resurgeai.core.model.ProgressPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Which file of a photo: the full image or the small one for grids. */
enum class PhotoVariant(val file: String) { FULL("full.bin"), THUMB("thumb.bin") }

/**
 * Progress photos, local-first and end-to-end encrypted:
 *   users/{uid}/progressPhotos/{id}                 takenAtMs, localDate, width, height, uploaded
 *   storage resurge/users/{uid}/progress/{id}/{full,thumb}.bin   ciphertext only
 * Each file is encrypted with the user's photo key ([PhotoKeys]) before it's written anywhere; the
 * same ciphertext is kept on the device (no-backup storage) and uploaded, so the device copy doubles
 * as the cache. The associated data binds each file to its account, photo and variant.
 */
@Singleton
class ProgressRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val keys: PhotoKeys,
) {
    private fun uid() = auth.currentUser?.uid
    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("progressPhotos")

    /** Every photo, oldest first. */
    fun photos(): Flow<List<ProgressPhoto>> {
        val uid = uid() ?: return flowOf(emptyList())
        return listen(collection(uid).orderBy("takenAtMs", Query.Direction.ASCENDING)) { docs -> docs.mapNotNull(::toPhoto) }
    }

    /**
     * Encrypts and saves a photo on the device, records it, and queues the upload. [full] and [thumb]
     * are JPEGs already stripped of metadata. Returns the new photo's id.
     */
    suspend fun add(full: ByteArray, thumb: ByteArray, takenAt: Instant, width: Int, height: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        withContext(Dispatchers.IO) {
            val uid = uid() ?: throw PhotoKeyLockedException()
            val aead = keys.aead()
            val doc = collection(uid).document()
            val dir = localDir(uid, doc.id).apply { mkdirs() }
            File(dir, PhotoVariant.FULL.file).writeBytes(aead.encrypt(full, ad(uid, doc.id, PhotoVariant.FULL)))
            File(dir, PhotoVariant.THUMB.file).writeBytes(aead.encrypt(thumb, ad(uid, doc.id, PhotoVariant.THUMB)))
            // Not awaited: Firestore queues the write offline and the listener shows it straight away.
            doc.set(
                mapOf(
                    "takenAtMs" to takenAt.toEpochMilli(),
                    "localDate" to takenAt.atZone(zone).toLocalDate().toString(),
                    "width" to width,
                    "height" to height,
                    "uploaded" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            scheduleUpload()
            doc.id
        }

    /**
     * The decrypted JPEG: from the device copy, else downloaded (and kept). Null when the file
     * isn't anywhere yet (still uploading from another phone) or can't be read.
     */
    suspend fun image(photo: ProgressPhoto, variant: PhotoVariant): ByteArray? = withContext(Dispatchers.IO) {
        val uid = uid() ?: return@withContext null
        val local = File(localDir(uid, photo.id), variant.file)
        val sealed = if (local.exists()) local.readBytes() else download(uid, photo.id, variant)?.also {
            local.parentFile?.mkdirs()
            local.writeBytes(it)
        }
        sealed ?: return@withContext null
        runCatching { keys.aead().decrypt(sealed, ad(uid, photo.id, variant)) }
            .onFailure { if (it !is PhotoKeyLockedException) Log.w(TAG, "Couldn't decrypt ${photo.id}/${variant.file}", it) }
            .getOrNull()
    }

    /** Downloads the encrypted files of [ids] that aren't on this phone yet, so a reel plays at once. */
    suspend fun prefetch(ids: List<String>) = withContext(Dispatchers.IO) {
        val uid = uid() ?: return@withContext
        ids.forEach { id ->
            PhotoVariant.entries.forEach { v ->
                val local = File(localDir(uid, id), v.file)
                if (!local.exists()) download(uid, id, v)?.let {
                    local.parentFile?.mkdirs()
                    local.writeBytes(it)
                }
            }
        }
    }

    /** Removes the photo everywhere: the record, both encrypted files in the cloud and on the device. */
    suspend fun delete(photo: ProgressPhoto) = withContext(Dispatchers.IO) {
        val uid = uid() ?: return@withContext
        collection(uid).document(photo.id).delete()
        localDir(uid, photo.id).deleteRecursively()
        PhotoVariant.entries.forEach { v ->
            runCatching { remote(uid, photo.id, v).delete().await() }
                .onFailure { e -> if ((e as? StorageException)?.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) Log.w(TAG, "Remote delete failed", e) }
        }
    }

    /** Deletes every photo, for starting over when their key is lost (nobody can open them anyway). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val uid = uid() ?: return@withContext
        collection(uid).get().await().documents.forEach { doc ->
            delete(ProgressPhoto(doc.id, Instant.EPOCH, 0, 0))
        }
        File(context.noBackupFilesDir, "progress/$uid").deleteRecursively()
    }

    /** Uploads every photo this device holds that isn't backed up yet. True when nothing is left. */
    internal suspend fun uploadPending(): Boolean = withContext(Dispatchers.IO) {
        val uid = uid() ?: return@withContext true
        val pending = collection(uid).whereEqualTo("uploaded", false).get().await().documents
        var done = true
        for (doc in pending) {
            val dir = localDir(uid, doc.id)
            if (!dir.exists()) continue // taken on another phone; that phone uploads it
            val ok = runCatching {
                PhotoVariant.entries.forEach { v ->
                    val meta = StorageMetadata.Builder().setContentType("application/octet-stream").build()
                    remote(uid, doc.id, v).putBytes(File(dir, v.file).readBytes(), meta).await()
                }
                doc.reference.update(mapOf("uploaded" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
            }.onFailure { Log.w(TAG, "Upload of ${doc.id} failed", it) }.isSuccess
            done = done && ok
        }
        done
    }

    /** Queues the backup. Called after each photo and whenever the app starts, so nothing waits on the user. */
    fun scheduleUpload() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ProgressUploadWorker.NAME,
            // Replace, so opening the app skips whatever backoff a failed attempt left behind.
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ProgressUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    private suspend fun download(uid: String, id: String, variant: PhotoVariant): ByteArray? =
        runCatching { remote(uid, id, variant).getBytes(MAX_BYTES).await() }
            .onFailure { e -> if ((e as? StorageException)?.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) Log.w(TAG, "Download of $id failed", e) }
            .getOrNull()

    private fun remote(uid: String, id: String, variant: PhotoVariant) =
        storage.reference.child("resurge/users/$uid/progress/$id/${variant.file}")

    private fun localDir(uid: String, id: String) = File(context.noBackupFilesDir, "progress/$uid/$id")

    private fun toPhoto(d: DocumentSnapshot): ProgressPhoto? = ProgressPhoto(
        id = d.id,
        takenAt = d.getLong("takenAtMs")?.let(Instant::ofEpochMilli) ?: return null,
        width = d.getLong("width")?.toInt() ?: 0,
        height = d.getLong("height")?.toInt() ?: 0,
        uploaded = d.getBoolean("uploaded") ?: true,
    )

    companion object {
        private const val TAG = "ProgressRepository"
        /** Encrypted full images are well under 1 MB; the storage rules cap them at 4 MB. */
        private const val MAX_BYTES = 4L * 1024 * 1024

        internal fun ad(uid: String, id: String, variant: PhotoVariant) = "$uid/$id/${variant.file}".toByteArray()
    }
}
