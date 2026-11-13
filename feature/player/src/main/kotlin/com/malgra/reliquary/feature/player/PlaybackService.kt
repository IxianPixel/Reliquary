package com.malgra.reliquary.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.malgra.reliquary.core.data.di.ApplicationScope
import com.malgra.reliquary.core.data.di.NotificationIcon
import com.malgra.reliquary.core.data.progress.ProgressRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Owns the player, so playback carries on when the app is in the background
 * and shows in the system media controls. Media, thumbnails for the
 * notification, and later local files all go through the signed-in client.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var progress: ProgressRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var notificationIcon: NotificationIcon

    private var session: MediaSession? = null
    private var tracker: ProgressTracker? = null

    override fun onCreate() {
        super.onCreate()
        val dataSource = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttpClient))
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
        tracker = ProgressTracker(player, progress, appScope)

        val builder = MediaSession.Builder(this, player)
            .setCallback(ResolveMediaUris)
            .setBitmapLoader(
                CacheBitmapLoader(DataSourceBitmapLoader.Builder(this).setDataSourceFactory(dataSource).build()),
            )
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        session = builder.build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(notificationIcon.smallIcon) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops playback, as people expect of a video app. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        tracker?.release()
        tracker = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Items sent through a controller lose their URI on the way, so the app
     * puts it in the request metadata and it's restored here.
     */
    private object ResolveMediaUris : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: return@map item
                item.buildUpon()
                    .setUri(uri)
                    .setSubtitleConfigurations(item.subtitleConfigurationsFromRequest())
                    .build()
            }.toMutableList(),
        )
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
