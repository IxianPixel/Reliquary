package com.malgra.reliquary.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.malgra.reliquary.core.data.library.LibraryCache
import com.malgra.reliquary.core.data.library.RoomLibraryCache
import com.malgra.reliquary.core.data.session.KeystoreSessionCipher
import com.malgra.reliquary.core.data.session.SessionCipher
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.network.auth.SessionHolder
import dagger.Binds
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

/** A scope that lives as long as the app, for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** DataStore holding the encrypted session and the last server and username. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionStore

/** DataStore holding the user's app settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    @SessionStore
    fun sessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }

    @Provides
    @Singleton
    @SettingsStore
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
interface DataBindings {
    @Binds fun sessionHolder(repository: SessionRepository): SessionHolder

    @Binds fun sessionCipher(cipher: KeystoreSessionCipher): SessionCipher

    @Binds fun libraryCache(cache: RoomLibraryCache): LibraryCache
}
