package com.malgra.reliquary.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.KeepOfflineRule
import com.malgra.reliquary.core.ui.resources.Res
import com.malgra.reliquary.core.ui.resources.keep_offline_button
import com.malgra.reliquary.core.ui.resources.keep_offline_button_active
import com.malgra.reliquary.core.ui.resources.keep_offline_cancel
import com.malgra.reliquary.core.ui.resources.keep_offline_count
import com.malgra.reliquary.core.ui.resources.keep_offline_count_body
import com.malgra.reliquary.core.ui.resources.keep_offline_delete
import com.malgra.reliquary.core.ui.resources.keep_offline_delete_note
import com.malgra.reliquary.core.ui.resources.keep_offline_estimate
import com.malgra.reliquary.core.ui.resources.keep_offline_estimate_unknown
import com.malgra.reliquary.core.ui.resources.keep_offline_fewer
import com.malgra.reliquary.core.ui.resources.keep_offline_intro
import com.malgra.reliquary.core.ui.resources.keep_offline_more
import com.malgra.reliquary.core.ui.resources.keep_offline_remove
import com.malgra.reliquary.core.ui.resources.keep_offline_remove_body
import com.malgra.reliquary.core.ui.resources.keep_offline_save
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_any_network
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_latest
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_latest_unwatched
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_remove
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_whole
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_whole_unwatched
import com.malgra.reliquary.core.ui.resources.keep_offline_summary_wifi
import com.malgra.reliquary.core.ui.resources.keep_offline_title
import com.malgra.reliquary.core.ui.resources.keep_offline_unwatched
import com.malgra.reliquary.core.ui.resources.keep_offline_unwatched_body
import com.malgra.reliquary.core.ui.resources.keep_offline_whole_playlist
import com.malgra.reliquary.core.ui.resources.keep_offline_whole_playlist_body
import com.malgra.reliquary.core.ui.resources.keep_offline_wifi
import com.malgra.reliquary.core.ui.resources.keep_offline_wifi_body
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "Keep offline" sheet from the mockup, for creating or editing a rule.
 * [playlistSize] is the number of archived videos, for a whole-playlist estimate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepOfflineSheet(
    initial: KeepOfflineRule,
    isExisting: Boolean,
    typicalVideoBytes: Long?,
    playlistSize: Int?,
    onSave: (KeepOfflineRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isPlaylist = initial.kind == KeepOfflineRule.Kind.Playlist
    var whole by rememberSaveable { mutableStateOf(isPlaylist && initial.keepCount == null) }
    var count by rememberSaveable { mutableStateOf(initial.keepCount ?: KeepOfflineRule.DEFAULT_KEEP_COUNT) }
    var unwatched by rememberSaveable { mutableStateOf(initial.unwatchedOnly) }
    var wifi by rememberSaveable { mutableStateOf(initial.wifiOnly) }
    var remove by rememberSaveable { mutableStateOf(initial.removeWhenWatched) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                stringResource(Res.string.keep_offline_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.keep_offline_intro, initial.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            if (isPlaylist) {
                SwitchRow(Res.string.keep_offline_whole_playlist, Res.string.keep_offline_whole_playlist_body, whole) { whole = it }
            }
            if (!whole) {
                CountRow(count, onChange = { count = it.coerceIn(1, KeepOfflineRule.MAX_KEEP_COUNT) })
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SwitchRow(Res.string.keep_offline_unwatched, Res.string.keep_offline_unwatched_body, unwatched) { unwatched = it }
            SwitchRow(Res.string.keep_offline_wifi, Res.string.keep_offline_wifi_body, wifi) { wifi = it }
            SwitchRow(Res.string.keep_offline_remove, Res.string.keep_offline_remove_body, remove) { remove = it }

            Estimate(typicalVideoBytes, videos = if (whole) playlistSize else count)

            if (isExisting) {
                Text(
                    stringResource(Res.string.keep_offline_delete_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                if (isExisting) {
                    TextButton(onClick = onDelete) { Text(stringResource(Res.string.keep_offline_delete)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.keep_offline_cancel)) }
                Button(onClick = {
                    onSave(
                        initial.copy(
                            keepCount = if (whole) null else count,
                            unwatchedOnly = unwatched,
                            wifiOnly = wifi,
                            removeWhenWatched = remove,
                        ),
                    )
                }) { Text(stringResource(Res.string.keep_offline_save)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: StringResource, body: StringResource, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CountRow(count: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.keep_offline_count), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.keep_offline_count_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedIconButton(onClick = { onChange(count - 1) }, enabled = count > 1) {
            Icon(painterResource(ReliquaryIcons.Minus), contentDescription = stringResource(Res.string.keep_offline_fewer))
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(40.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedIconButton(onClick = { onChange(count + 1) }, enabled = count < KeepOfflineRule.MAX_KEEP_COUNT) {
            Icon(painterResource(ReliquaryIcons.Plus), contentDescription = stringResource(Res.string.keep_offline_more))
        }
    }
}

@Composable
private fun Estimate(typicalVideoBytes: Long?, videos: Int?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painterResource(ReliquaryIcons.Downloads),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                if (typicalVideoBytes != null && videos != null && videos > 0) {
                    pluralStringResource(
                        Res.plurals.keep_offline_estimate,
                        videos,
                        fileSizeText(typicalVideoBytes * videos),
                        videos,
                    )
                } else {
                    stringResource(Res.string.keep_offline_estimate_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "Latest 5 unwatched · Wi-Fi only · remove when watched", as on the Downloads tab. */
@Composable
fun keepOfflineSummary(rule: KeepOfflineRule): String {
    val count = rule.keepCount
    val amount = when {
        count == null && rule.unwatchedOnly -> stringResource(Res.string.keep_offline_summary_whole_unwatched)
        count == null -> stringResource(Res.string.keep_offline_summary_whole)
        rule.unwatchedOnly -> pluralStringResource(Res.plurals.keep_offline_summary_latest_unwatched, count, count)
        else -> pluralStringResource(Res.plurals.keep_offline_summary_latest, count, count)
    }
    return metaLine(
        amount,
        stringResource(if (rule.wifiOnly) Res.string.keep_offline_summary_wifi else Res.string.keep_offline_summary_any_network),
        if (rule.removeWhenWatched) stringResource(Res.string.keep_offline_summary_remove) else null,
    )
}

/** Opens the keep-offline sheet; says so when a rule already keeps this offline. */
@Composable
fun KeepOfflineButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(if (active) ReliquaryIcons.Check else ReliquaryIcons.Downloads),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(if (active) Res.string.keep_offline_button_active else Res.string.keep_offline_button),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
