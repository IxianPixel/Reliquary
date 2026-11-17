package com.malgra.reliquary.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.malgra.reliquary.core.data.AndroidDeviceNetwork
import com.malgra.reliquary.core.data.DeviceNetwork
import com.malgra.reliquary.core.data.download.AndroidDownloadStorage
import com.malgra.reliquary.core.data.download.DownloadStorage
import com.malgra.reliquary.core.data.library.LibraryCache
import com.malgra.reliquary.core.data.library.RoomLibraryCache
import com.malgra.reliquary.core.data.session.KeystoreSessionCipher
import com.malgra.reliquary.core.data.session.SessionCipher
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.settings.AndroidPreferencesLocation
import com.malgra.reliquary.core.data.settings.PreferencesLocation
import com.malgra.reliquary.core.network.auth.SessionHolder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

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
    fun sessionDataStore(location: PreferencesLocation): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { location.file(PreferencesLocation.SESSION) }

    @Provides
    @Singleton
    @SettingsStore
    fun settingsDataStore(location: PreferencesLocation): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { location.file(PreferencesLocation.SETTINGS) }
}

@Module
@InstallIn(SingletonComponent::class)
interface DataBindings {
    @Binds fun sessionHolder(repository: SessionRepository): SessionHolder

    @Binds fun sessionCipher(cipher: KeystoreSessionCipher): SessionCipher

    @Binds fun libraryCache(cache: RoomLibraryCache): LibraryCache

    @Binds fun downloadStorage(storage: AndroidDownloadStorage): DownloadStorage

    @Binds fun deviceNetwork(network: AndroidDeviceNetwork): DeviceNetwork

    @Binds fun preferencesLocation(location: AndroidPreferencesLocation): PreferencesLocation
}
