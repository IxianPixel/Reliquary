package com.malgra.reliquary.feature.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.malgra.reliquary.core.model.ChannelSettings
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.feature.channels.resources.Res
import com.malgra.reliquary.feature.channels.resources.channel_settings_at_least
import com.malgra.reliquary.feature.channels.resources.channel_settings_auto_delete
import com.malgra.reliquary.feature.channels.resources.channel_settings_auto_delete_body
import com.malgra.reliquary.feature.channels.resources.channel_settings_cancel
import com.malgra.reliquary.feature.channels.resources.channel_settings_downloads
import com.malgra.reliquary.feature.channels.resources.channel_settings_format
import com.malgra.reliquary.feature.channels.resources.channel_settings_format_body
import com.malgra.reliquary.feature.channels.resources.channel_settings_index_playlists
import com.malgra.reliquary.feature.channels.resources.channel_settings_index_playlists_body
import com.malgra.reliquary.feature.channels.resources.channel_settings_intro
import com.malgra.reliquary.feature.channels.resources.channel_settings_load_failed
import com.malgra.reliquary.feature.channels.resources.channel_settings_offline
import com.malgra.reliquary.feature.channels.resources.channel_settings_page_sizes
import com.malgra.reliquary.feature.channels.resources.channel_settings_page_sizes_body
import com.malgra.reliquary.feature.channels.resources.channel_settings_retry
import com.malgra.reliquary.feature.channels.resources.channel_settings_save
import com.malgra.reliquary.feature.channels.resources.channel_settings_save_failed
import com.malgra.reliquary.feature.channels.resources.channel_settings_save_offline
import com.malgra.reliquary.feature.channels.resources.channel_settings_saving
import com.malgra.reliquary.feature.channels.resources.channel_settings_server_default
import com.malgra.reliquary.feature.channels.resources.channel_settings_shorts
import com.malgra.reliquary.feature.channels.resources.channel_settings_sponsorblock
import com.malgra.reliquary.feature.channels.resources.channel_settings_sponsorblock_body
import com.malgra.reliquary.feature.channels.resources.channel_settings_sponsorblock_off
import com.malgra.reliquary.feature.channels.resources.channel_settings_sponsorblock_on
import com.malgra.reliquary.feature.channels.resources.channel_settings_sponsorblock_server
import com.malgra.reliquary.feature.channels.resources.channel_settings_streams
import com.malgra.reliquary.feature.channels.resources.channel_settings_title
import com.malgra.reliquary.feature.channels.resources.channel_settings_videos
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The channel's own settings on the server, like TA's channel customisation. Only shown to admins. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChannelSettingsSheet(
    channelName: String,
    state: ChannelSettingsState,
    onSave: (ChannelSettings) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(Res.string.channel_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.channel_settings_intro, channelName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            val settings = state.settings
            when {
                settings != null -> SettingsForm(settings, state.saving, state.saveError, onSave, onDismiss)
                state.loadError != null -> LoadFailed(state.loadError, onRetry, onDismiss)
                else -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                ) { LoadingIndicator() }
            }
        }
    }
}

@Composable
private fun SettingsForm(
    initial: ChannelSettings,
    saving: Boolean,
    saveError: LoadError?,
    onSave: (ChannelSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var videos by rememberSaveable(initial) { mutableStateOf(initial.videosPageSize?.toString().orEmpty()) }
    var shorts by rememberSaveable(initial) { mutableStateOf(initial.shortsPageSize?.toString().orEmpty()) }
    var streams by rememberSaveable(initial) { mutableStateOf(initial.streamsPageSize?.toString().orEmpty()) }
    var format by rememberSaveable(initial) { mutableStateOf(initial.downloadFormat.orEmpty()) }
    var autoDelete by rememberSaveable(initial) { mutableStateOf(initial.autoDeleteDays?.toString().orEmpty()) }
    var indexPlaylists by rememberSaveable(initial) { mutableStateOf(initial.indexPlaylists) }
    var sponsorBlock by rememberSaveable(initial) { mutableStateOf(SponsorBlockChoice.of(initial.sponsorBlock)) }

    val valid = listOf(videos, shorts, streams).all { it.isValidNumber(min = 0) } && autoDelete.isValidNumber(min = 1)

    SectionTitle(Res.string.channel_settings_page_sizes)
    Text(
        stringResource(Res.string.channel_settings_page_sizes_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NumberField(Res.string.channel_settings_videos, videos, min = 0) { videos = it }
    NumberField(Res.string.channel_settings_shorts, shorts, min = 0) { shorts = it }
    NumberField(Res.string.channel_settings_streams, streams, min = 0) { streams = it }

    HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 4.dp))
    SectionTitle(Res.string.channel_settings_downloads)
    OutlinedTextField(
        value = format,
        onValueChange = { format = it },
        label = { Text(stringResource(Res.string.channel_settings_format)) },
        placeholder = { Text(stringResource(Res.string.channel_settings_server_default)) },
        supportingText = { Text(stringResource(Res.string.channel_settings_format_body)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    NumberField(
        Res.string.channel_settings_auto_delete,
        autoDelete,
        min = 1,
        supporting = Res.string.channel_settings_auto_delete_body,
    ) { autoDelete = it }
    SwitchRow(Res.string.channel_settings_index_playlists, Res.string.channel_settings_index_playlists_body, indexPlaylists) {
        indexPlaylists = it
    }
    SponsorBlockRow(sponsorBlock) { sponsorBlock = it }

    if (saveError != null) {
        Text(
            stringResource(if (saveError == LoadError.Offline) Res.string.channel_settings_save_offline else Res.string.channel_settings_save_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.channel_settings_cancel)) }
        Button(
            enabled = valid && !saving,
            onClick = {
                onSave(
                    ChannelSettings(
                        videosPageSize = videos.toIntOrNull(),
                        shortsPageSize = shorts.toIntOrNull(),
                        streamsPageSize = streams.toIntOrNull(),
                        downloadFormat = format.trim().ifEmpty { null },
                        autoDeleteDays = autoDelete.toIntOrNull(),
                        indexPlaylists = indexPlaylists,
                        sponsorBlock = sponsorBlock.value,
                    ),
                )
            },
        ) { Text(stringResource(if (saving) Res.string.channel_settings_saving else Res.string.channel_settings_save)) }
    }
}

@Composable
private fun LoadFailed(error: LoadError, onRetry: () -> Unit, onCancel: () -> Unit) {
    Text(
        stringResource(if (error == LoadError.Offline) Res.string.channel_settings_offline else Res.string.channel_settings_load_failed),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 16.dp),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.channel_settings_cancel)) }
        Button(onClick = onRetry) { Text(stringResource(Res.string.channel_settings_retry)) }
    }
}

@Composable
private fun SectionTitle(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Blank means the server's setting. */
@Composable
private fun NumberField(label: StringResource, value: String, min: Int, supporting: StringResource? = null, onChange: (String) -> Unit) {
    val error = !value.isValidNumber(min)
    val message = when {
        error -> stringResource(Res.string.channel_settings_at_least, min)
        supporting != null -> stringResource(supporting)
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter(Char::isDigit).take(MAX_DIGITS)) },
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(Res.string.channel_settings_server_default)) },
        supportingText = if (message != null) { { Text(message) } } else null,
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
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

private enum class SponsorBlockChoice(val value: Boolean?) {
    Server(null),
    On(true),
    Off(false),
    ;

    companion object {
        fun of(value: Boolean?) = entries.first { it.value == value }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SponsorBlockRow(selected: SponsorBlockChoice, onSelect: (SponsorBlockChoice) -> Unit) {
    val label = stringResource(Res.string.channel_settings_sponsorblock)
    Column(Modifier.padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.channel_settings_sponsorblock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { contentDescription = label },
        ) {
            SponsorBlockChoice.entries.forEachIndexed { index, choice ->
                ToggleButton(
                    checked = choice == selected,
                    onCheckedChange = { onSelect(choice) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        SponsorBlockChoice.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(
                        stringResource(
                            when (choice) {
                                SponsorBlockChoice.Server -> Res.string.channel_settings_sponsorblock_server
                                SponsorBlockChoice.On -> Res.string.channel_settings_sponsorblock_on
                                SponsorBlockChoice.Off -> Res.string.channel_settings_sponsorblock_off
                            },
                        ),
                    )
                }
            }
        }
    }
}

private fun String.isValidNumber(min: Int): Boolean = isEmpty() || (toIntOrNull()?.let { it >= min } ?: false)

private const val MAX_DIGITS = 5
