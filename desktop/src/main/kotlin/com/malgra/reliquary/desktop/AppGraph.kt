package com.malgra.reliquary.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.malgra.reliquary.core.data.AlwaysConnected
import com.malgra.reliquary.core.data.AppDirectories
import com.malgra.reliquary.core.data.ConnectionMonitor
import com.malgra.reliquary.core.data.auth.AuthRepository
import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.download.FolderDownloadStorage
import com.malgra.reliquary.core.data.library.ChannelRepository
import com.malgra.reliquary.core.data.library.ChannelSettingsRepository
import com.malgra.reliquary.core.data.library.LibraryApi
import com.malgra.reliquary.core.data.library.PlaylistRepository
import com.malgra.reliquary.core.data.library.RoomLibraryCache
import com.malgra.reliquary.core.data.library.SearchRepository
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.data.progress.ProgressSyncer
import com.malgra.reliquary.core.data.queue.QueueRepository
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import com.malgra.reliquary.core.data.session.AccountRepository
import com.malgra.reliquary.core.data.session.KeyringSessionCipher
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.data.settings.DesktopPreferencesLocation
import com.malgra.reliquary.core.data.settings.PreferencesLocation
import com.malgra.reliquary.core.database.reliquaryDatabase
import com.malgra.reliquary.core.download.DesktopDownloadScheduler
import com.malgra.reliquary.core.download.DownloadExtras
import com.malgra.reliquary.core.download.DownloadJob
import com.malgra.reliquary.core.download.MediaDownloader
import com.malgra.reliquary.core.network.ReachabilityInterceptor
import com.malgra.reliquary.core.network.ServerReachability
import com.malgra.reliquary.core.network.TubeArchivistJson
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.AuthInterceptor
import com.malgra.reliquary.core.network.tubeArchivistHttpClient
import com.malgra.reliquary.core.sync.DesktopLibrarySyncScheduler
import com.malgra.reliquary.core.sync.DesktopProgressSyncScheduler
import com.malgra.reliquary.core.sync.LibrarySync
import com.malgra.reliquary.core.sync.ProgressSync
import com.malgra.reliquary.feature.player.DesktopPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Provider

/**
 * The desktop app's singletons, wired by hand: Hilt builds the same graph on
 * Android. Each is made on first use, so the order below doesn't matter.
 */
class AppGraph(val directories: AppDirectories, logHttp: Boolean) {

    /** Work that must outlive a screen: syncs and downloads. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { reliquaryDatabase(directories.database) }

    private val preferences = DesktopPreferencesLocation(directories)
    private val sessionStore by lazy { PreferenceDataStoreFactory.create { preferences.file(PreferencesLocation.SESSION) } }
    private val settingsStore by lazy { PreferenceDataStoreFactory.create { preferences.file(PreferencesLocation.SETTINGS) } }

    val sessions by lazy { SessionRepository(sessionStore, KeyringSessionCipher(), applicationScope) }
    val settings by lazy { AppSettings(settingsStore) }

    private val reachability = ServerReachability()

    /** The one client for the API, thumbnails and downloads, so they share credentials and connections. */
    val okHttpClient by lazy {
        tubeArchivistHttpClient(AuthInterceptor(sessions), ReachabilityInterceptor(sessions, reachability), logHttp)
    }
    private val network by lazy { TubeArchivistNetwork(okHttpClient, TubeArchivistJson) }
    private val library by lazy { LibraryApi(network, sessions) }

    val connection by lazy { ConnectionMonitor(AlwaysConnected, reachability, network, sessions, applicationScope) }
    val account by lazy { AccountRepository(sessions, network, connection, applicationScope) }

    val videos by lazy { VideoRepository(database, library) }
    val channels by lazy { ChannelRepository(database, library) }
    val playlists by lazy { PlaylistRepository(database, library) }
    val search by lazy { SearchRepository(database, library) }
    val queue by lazy { QueueRepository(library) }
    val channelSettings by lazy { ChannelSettingsRepository(library) }

    private val downloadFiles by lazy { DownloadFiles(FolderDownloadStorage(directories.downloads)) }
    private val mediaDownloader by lazy { MediaDownloader(okHttpClient, downloadFiles, database.downloadDao()) }
    private val downloadExtras by lazy { DownloadExtras(mediaDownloader, videos, database.downloadDao()) }
    val downloadScheduler by lazy {
        DesktopDownloadScheduler(applicationScope, DownloadJob(database, mediaDownloader, downloadExtras, downloadFiles), database)
    }
    val downloads by lazy { DownloadRepository(database, downloadFiles, downloadScheduler, settings) }

    // LibrarySync needs KeepOfflineRepository, which needs this scheduler: the Provider breaks the loop.
    val librarySync: DesktopLibrarySyncScheduler by lazy {
        DesktopLibrarySyncScheduler(applicationScope, Provider { LibrarySync(keepOffline, downloadExtras) })
    }
    val keepOffline: KeepOfflineRepository by lazy { KeepOfflineRepository(database, library, downloads, librarySync) }

    val progressSync by lazy {
        DesktopProgressSyncScheduler(applicationScope, ProgressSync(ProgressSyncer(database, library), keepOffline))
    }
    val progress by lazy { ProgressRepository(database, progressSync) }

    val auth by lazy {
        AuthRepository(network, sessions, RoomLibraryCache(database, downloadScheduler, downloadFiles), progressSync)
    }

    private val playbackLazy = lazy { DesktopPlayback(okHttpClient, progress, settings, applicationScope) }

    /** The one player, for the app's lifetime: what PlaybackService is on Android. */
    val playback by playbackLazy

    /** What the Android app does in `ReliquaryApplication.onCreate`, plus resuming downloads, which end with the app here. */
    fun start() {
        downloadScheduler.resumeUnfinished()
        progressSync.requestSync()
        librarySync.schedulePeriodic()
    }

    /** Saves the playing video's place, stops syncs and downloads (they pick up again next launch) and closes the database. */
    fun close() {
        if (playbackLazy.isInitialized()) playback.release() // needs the app scope, so first
        applicationScope.cancel()
        database.close()
    }
}
