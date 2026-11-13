package com.malgra.reliquary.core.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.Video

/** What a video's menu can do. The app shell provides it; without one, rows have no menu. */
interface VideoActions {
    fun download(videoId: String)
    fun removeDownload(videoId: String)
    fun setWatched(videoId: String, watched: Boolean)
}

val LocalVideoActions = staticCompositionLocalOf<VideoActions?> { null }

/** Download (or cancel, or remove) and mark watched, for one video. */
@Composable
internal fun VideoMenu(video: Video, actions: VideoActions, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (video.download) {
            null, DownloadStatus.Failed -> DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_menu_download)) },
                leadingIcon = { Icon(painterResource(ReliquaryIcons.Downloads), contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.download(video.id)
                },
            )
            DownloadStatus.Completed -> DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_menu_remove_download)) },
                leadingIcon = { Icon(painterResource(ReliquaryIcons.Close), contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.removeDownload(video.id)
                },
            )
            else -> DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_menu_cancel_download)) },
                leadingIcon = { Icon(painterResource(ReliquaryIcons.Close), contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.removeDownload(video.id)
                },
            )
        }
        DropdownMenuItem(
            text = {
                Text(stringResource(if (video.watched) R.string.ui_menu_mark_unwatched else R.string.ui_menu_mark_watched))
            },
            leadingIcon = { Icon(painterResource(ReliquaryIcons.CheckCircle), contentDescription = null) },
            onClick = {
                onDismiss()
                actions.setWatched(video.id, !video.watched)
            },
        )
    }
}
