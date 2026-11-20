package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * The desktop app's player, for its lifetime: what PlaybackService is on
 * Android. It saves progress and skips SponsorBlock segments while it plays.
 * [client] is the signed-in client, [scope] the app's.
 */
class DesktopPlayback(
    client: OkHttpClient,
    progress: ProgressRepository,
    settings: AppSettings,
    scope: CoroutineScope,
) {
    private val main = CoroutineScope(SupervisorJob(scope.coroutineContext.job) + Dispatchers.Main)
    private val desktopPlayer = DesktopVideoPlayer(client, main)

    /** Hand this to PlayerSheet. */
    val player: VideoPlayer get() = desktopPlayer

    @Volatile private var skipSponsors = true

    private val tracker = ProgressTracker(desktopPlayer, progress.asPlaybackProgress(), scope)
    // libVLC seeks exactly, so the skip rules need no keyframe slack, as on Android.
    private val skipper = SponsorSkipper(desktopPlayer, enabled = { skipSponsors }) { SponsorSkips.emit(it) }

    init {
        main.launch { settings.skipSponsorSegments.collect { skipSponsors = it } }
    }

    /** Saves the place a last time and stops. Call on quit, before the app scope is cancelled. */
    fun release() {
        tracker.release()
        skipper.release()
        desktopPlayer.release()
        main.cancel()
    }
}
