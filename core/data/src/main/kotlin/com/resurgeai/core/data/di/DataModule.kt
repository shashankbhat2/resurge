package com.resurgeai.core.data.di

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** OAuth web client ID used as the server client ID for Sign in with Google. Provided by :app. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebClientId

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Resurge shares a Firebase project with other apps, so its data lives in a named database. */
const val FIRESTORE_DATABASE = "resurge"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "resurge_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DATABASE)

    @Provides
    @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")

    /** The project's default bucket, shared with other apps; Resurge's files live under resurge/. */
    @Provides
    @Singleton
    fun storage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun credentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
