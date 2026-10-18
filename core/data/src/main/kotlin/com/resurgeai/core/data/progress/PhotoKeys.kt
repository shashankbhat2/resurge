package com.resurgeai.core.data.progress

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.resurgeai.core.model.PhotoKeyState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a photo is read or written before the key is on this device. */
class PhotoKeyLockedException : IllegalStateException("Progress photo key isn't on this device")

/**
 * The key that end-to-end encrypts progress photos, handled without the user ever seeing it. It's
 * made on the phone and never leaves it readable. Three copies:
 *  - on this device, wrapped by an Android Keystore key;
 *  - in Firestore (users/{uid}/progressKey/current), wrapped by a random restore secret;
 *  - that restore secret in Block Store, end-to-end encrypted by the phone's screen lock and carried
 *    to a new phone by Google's device backup and restore.
 * A new phone set up without restoring from a backup can't open earlier photos (nor can anyone
 * else); it can only start over ([startFresh]).
 */
@Singleton
class PhotoKeys @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val blockstore by lazy { Blockstore.getClient(context) }
    private val mutex = Mutex()
    private var cached: Pair<String, Aead>? = null

    private val _state = MutableStateFlow(PhotoKeyState.LOADING)
    val state: StateFlow<PhotoKeyState> = _state.asStateFlow()

    init {
        runCatching { AeadConfig.register() }.onFailure { Log.e(TAG, "Tink AEAD registration failed", it) }
    }

    private fun uid(): String = auth.currentUser?.uid ?: throw PhotoKeyLockedException()
    private fun keyDoc(uid: String) = firestore.collection("users").document(uid).collection("progressKey").document("current")

    /** Works out the state: the device copy is enough; otherwise Block Store is tried quietly. */
    suspend fun refresh(): PhotoKeyState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val uid = auth.currentUser?.uid ?: return@withLock PhotoKeyState.LOADING.also { _state.value = it }
            val next = if (localAead(uid) != null) {
                ensureInBlockstore(uid)
                PhotoKeyState.READY
            } else {
                val doc = runCatching { keyDoc(uid).get().await() }.getOrNull()
                when {
                    doc == null -> PhotoKeyState.OFFLINE // nothing to go on: don't guess
                    !doc.exists() -> PhotoKeyState.NOT_SET_UP
                    restoreFromBlockstore(uid, doc.getBlob("wrapped")?.toBytes()) -> PhotoKeyState.READY
                    else -> PhotoKeyState.LOCKED
                }
            }
            _state.value = next
            next
        }
    }

    /** The photo cipher, or [PhotoKeyLockedException] when the key isn't on this device. */
    suspend fun aead(): Aead = withContext(Dispatchers.IO) {
        mutex.withLock { localAead(uid()) ?: throw PhotoKeyLockedException() }
    }

    /**
     * Makes the key, quietly, before the first photo. Checks the server first, so a phone that's
     * merely offline can never replace a key that already protects photos.
     */
    suspend fun setUp() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val uid = uid()
            if (localAead(uid) != null) return@withLock
            check(!keyDoc(uid).get(Source.SERVER).await().exists()) { "A photo key already exists" }
            create(uid)
        }
    }

    /**
     * For a phone that can't get the old key: the old key is deleted (the caller deletes the photos
     * it locked, which nobody can open now) and a new one is made.
     */
    suspend fun startFresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val uid = uid()
            keyDoc(uid).delete().await()
            create(uid)
        }
    }

    private suspend fun create(uid: String) {
        val handle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        val secret = ByteArray(SECRET_BYTES).also(SecureRandom()::nextBytes)
        keyDoc(uid).set(
            mapOf(
                "wrapped" to Blob.fromBytes(wrap(handle, secret, uid)),
                "keyId" to handle.keysetInfo.primaryKeyId.toLong(),
                "version" to VERSION,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        saveLocal(uid, handle, secret)
        storeInBlockstore(uid, secret)
        _state.value = PhotoKeyState.READY
    }

    // --- Device copy ---

    private fun localAead(uid: String): Aead? {
        cached?.takeIf { it.first == uid }?.let { return it.second }
        val stored = prefs.getString("keyset_$uid", null) ?: return null
        val handle = runCatching {
            TinkProtoKeysetFormat.parseEncryptedKeyset(Base64.decode(stored, Base64.NO_WRAP), deviceAead(), uid.toByteArray())
        }.onFailure { Log.w(TAG, "Device copy of the photo key is unreadable", it) }.getOrNull() ?: return null
        val aead = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        cached = uid to aead
        return aead
    }

    private fun saveLocal(uid: String, handle: KeysetHandle, secret: ByteArray) {
        val device = deviceAead()
        prefs.edit()
            .putString("keyset_$uid", Base64.encodeToString(TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, device, uid.toByteArray()), Base64.NO_WRAP))
            .putString("secret_$uid", Base64.encodeToString(device.encrypt(secret, "secret:$uid".toByteArray()), Base64.NO_WRAP))
            .commit()
        cached = uid to handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun deviceAead(): Aead {
        if (!AndroidKeystore.hasKey(DEVICE_KEY)) AndroidKeystore.generateNewAes256GcmKey(DEVICE_KEY)
        return AndroidKeystore.getAead(DEVICE_KEY)
    }

    // --- Block Store ---

    /** Puts the restore secret in Block Store if that hasn't worked yet (say, it failed at setup). */
    private suspend fun ensureInBlockstore(uid: String) {
        if (prefs.getBoolean("blockstore_$uid", false)) return
        val stored = prefs.getString("secret_$uid", null) ?: return
        val secret = runCatching { deviceAead().decrypt(Base64.decode(stored, Base64.NO_WRAP), "secret:$uid".toByteArray()) }.getOrNull() ?: return
        storeInBlockstore(uid, secret)
    }

    private suspend fun storeInBlockstore(uid: String, secret: ByteArray) {
        runCatching {
            blockstore.storeBytes(StoreBytesData.Builder().setKey(blockstoreKey(uid)).setBytes(secret).setShouldBackupToCloud(true).build()).await()
        }.onSuccess { prefs.edit().putBoolean("blockstore_$uid", true).apply() }
            .onFailure { Log.w(TAG, "Saving the restore secret to Block Store failed", it) }
    }

    private suspend fun restoreFromBlockstore(uid: String, wrapped: ByteArray?): Boolean {
        wrapped ?: return false
        val secret = runCatching {
            blockstore.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(blockstoreKey(uid))).build()).await()
                .blockstoreDataMap[blockstoreKey(uid)]?.bytes
        }.onFailure { Log.w(TAG, "Block Store read failed", it) }.getOrNull() ?: return false
        val handle = unwrap(wrapped, secret, uid) ?: return false
        saveLocal(uid, handle, secret)
        prefs.edit().putBoolean("blockstore_$uid", true).apply()
        return true
    }

    private fun blockstoreKey(uid: String) = "resurge_progress_$uid".take(64)

    companion object {
        private const val TAG = "PhotoKeys"
        private const val PREFS = "progress_keys"
        private const val DEVICE_KEY = "resurge_progress_device"
        private const val VERSION = 1L
        internal const val SECRET_BYTES = 32

        /** The restore secret (256 random bits) as a wrapping cipher bound to the account. */
        internal fun secretAead(secret: ByteArray, uid: String): Aead =
            AesGcmJce(Hkdf.computeHkdf("HMACSHA256", secret, uid.toByteArray(), "resurge-progress-wrap-v1".toByteArray(), 32))

        internal fun wrap(handle: KeysetHandle, secret: ByteArray, uid: String): ByteArray =
            TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, secretAead(secret, uid), "progressKey:$uid".toByteArray())

        internal fun unwrap(wrapped: ByteArray, secret: ByteArray, uid: String): KeysetHandle? = try {
            TinkProtoKeysetFormat.parseEncryptedKeyset(wrapped, secretAead(secret, uid), "progressKey:$uid".toByteArray())
        } catch (_: GeneralSecurityException) {
            null
        }
    }
}
