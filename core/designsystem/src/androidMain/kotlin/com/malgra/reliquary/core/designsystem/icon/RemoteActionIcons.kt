package com.malgra.reliquary.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.malgra.reliquary.core.designsystem.R

/**
 * Android drawable IDs for the picture-in-picture controls: RemoteAction needs a
 * resource ID, which Compose resources don't have. Copies of the [ReliquaryIcons]
 * drawables of the same name; keep them in step.
 */
object RemoteActionIcons {
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val Pause = R.drawable.ic_pause
    @DrawableRes val Replay10 = R.drawable.ic_replay_10
    @DrawableRes val Forward10 = R.drawable.ic_forward_10
}
