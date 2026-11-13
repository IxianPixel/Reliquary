package com.malgra.reliquary.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.Comment
import com.malgra.reliquary.core.ui.compactCount
import com.malgra.reliquary.core.ui.formatDate
import com.malgra.reliquary.core.ui.metaLine

/** "Comments · 128" with the first comment as a taste; opens [onOpen]. */
@Composable
internal fun CommentsRow(comments: CommentsState.Loaded, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val first = comments.comments.first()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                metaLine(stringResource(R.string.player_comments), compactCount(comments.total.toLong())),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AuthorInitial(first.author, Modifier.size(24.dp))
                Text(
                    first.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** All the saved comments, each with its replies folded away until asked for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(comments: CommentsState.Loaded, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item(key = "title") {
                Text(
                    metaLine(stringResource(R.string.player_comments), compactCount(comments.total.toLong())),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            items(comments.comments, key = { it.id }) { comment ->
                CommentThread(comment)
            }
        }
    }
}

@Composable
private fun CommentThread(comment: Comment) {
    var showReplies by rememberSaveable(comment.id) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        CommentItem(comment, avatarSize = 32)
        if (comment.replies.isNotEmpty()) {
            TextButton(onClick = { showReplies = !showReplies }, modifier = Modifier.padding(start = 34.dp)) {
                Icon(
                    painterResource(if (showReplies) ReliquaryIcons.ChevronDown else ReliquaryIcons.ChevronRight),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (showReplies) {
                        stringResource(R.string.player_hide_replies)
                    } else {
                        pluralStringResource(R.plurals.player_replies, comment.replies.size, comment.replies.size)
                    },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (showReplies) {
                Column(Modifier.padding(start = 44.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    comment.replies.forEach { reply -> CommentItem(reply, avatarSize = 24) }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, avatarSize: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthorInitial(comment.author, Modifier.size(avatarSize.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (comment.byUploader) {
                    // The channel's own comments stand out, as on YouTube.
                    Text(
                        comment.author,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                } else {
                    Text(
                        comment.author,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                comment.published?.let {
                    Text(
                        formatDate(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            val likes = comment.likeCount?.takeIf { it > 0 }
            if (likes != null || comment.favorited) {
                Text(
                    metaLine(
                        likes?.let { pluralStringResource(R.plurals.player_comment_likes, it, compactCount(it.toLong())) },
                        if (comment.favorited) stringResource(R.string.player_comment_hearted) else null,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The author's first letter. Avatars would be fetched from YouTube, and the app
 * otherwise only talks to the Tube Archivist server, so it doesn't load them.
 */
@Composable
private fun AuthorInitial(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trimStart('@').firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
