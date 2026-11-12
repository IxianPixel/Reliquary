package com.malgra.reliquary

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler
import com.malgra.reliquary.core.data.rules.LibrarySyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ReliquaryApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    /** The shared client: thumbnails need the same credentials as the API. */
    @Inject lateinit var okHttpClient: Lazy<OkHttpClient>
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var progressSync: ProgressSyncScheduler
    @Inject lateinit var librarySync: LibrarySyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Push anything saved while the app was last offline or killed.
        progressSync.requestSync()
        // Keep-offline rules and downloaded videos' details, about every 12 hours.
        librarySync.schedulePeriodic()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() })) }
            .crossfade(true)
            .build()
}
