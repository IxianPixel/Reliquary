package com.malgra.reliquary.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.designsystem.theme.dynamicColorSupported
import com.malgra.reliquary.core.model.ThemeMode
import com.malgra.reliquary.core.ui.StatusBarScrim
import com.malgra.reliquary.core.ui.fileSizeText
import com.malgra.reliquary.core.ui.viewmodel.injectedViewModel
import com.malgra.reliquary.core.ui.wifiOnlySupported
import com.malgra.reliquary.feature.settings.resources.Res
import com.malgra.reliquary.feature.settings.resources.settings_dynamic_color
import com.malgra.reliquary.feature.settings.resources.settings_dynamic_color_body
import com.malgra.reliquary.feature.settings.resources.settings_section_account
import com.malgra.reliquary.feature.settings.resources.settings_section_appearance
import com.malgra.reliquary.feature.settings.resources.settings_section_downloads
import com.malgra.reliquary.feature.settings.resources.settings_section_playback
import com.malgra.reliquary.feature.settings.resources.settings_server_version
import com.malgra.reliquary.feature.settings.resources.settings_sign_out
import com.malgra.reliquary.feature.settings.resources.settings_signed_in_password
import com.malgra.reliquary.feature.settings.resources.settings_signed_in_token
import com.malgra.reliquary.feature.settings.resources.settings_skip_sponsors
import com.malgra.reliquary.feature.settings.resources.settings_skip_sponsors_body
import com.malgra.reliquary.feature.settings.resources.settings_storage_limit
import com.malgra.reliquary.feature.settings.resources.settings_storage_limit_body
import com.malgra.reliquary.feature.settings.resources.settings_storage_limit_body_none
import com.malgra.reliquary.feature.settings.resources.settings_storage_limit_cancel
import com.malgra.reliquary.feature.settings.resources.settings_storage_limit_gb
import com.malgra.reliquary.feature.settings.resources.settings_storage_limit_none
import com.malgra.reliquary.feature.settings.resources.settings_theme
import com.malgra.reliquary.feature.settings.resources.settings_theme_dark
import com.malgra.reliquary.feature.settings.resources.settings_theme_light
import com.malgra.reliquary.feature.settings.resources.settings_theme_system
import com.malgra.reliquary.feature.settings.resources.settings_title
import com.malgra.reliquary.feature.settings.resources.settings_unknown_user
import com.malgra.reliquary.feature.settings.resources.settings_wifi_only
import com.malgra.reliquary.feature.settings.resources.settings_wifi_only_body
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = injectedViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val skipSponsors by viewModel.skipSponsorSegments.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val storageLimit by viewModel.storageLimitBytes.collectAsStateWithLifecycle()
    var choosingLimit by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )

            SectionTitle(stringResource(Res.string.settings_section_appearance))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_theme)) },
                supportingContent = {
                    ThemeChoice(themeMode, viewModel::setThemeMode, Modifier.padding(top = 8.dp))
                },
                leadingContent = { Icon(painterResource(ReliquaryIcons.Theme), contentDescription = null) },
            )
            if (dynamicColorSupported) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(Res.string.settings_dynamic_color_body)) },
                    leadingContent = { Icon(painterResource(ReliquaryIcons.Palette), contentDescription = null) },
                    trailingContent = { Switch(checked = dynamicColor, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = dynamicColor,
                        role = Role.Switch,
                        onValueChange = viewModel::setDynamicColor,
                    ),
                )
            }

            SectionTitle(stringResource(Res.string.settings_section_downloads))
            if (wifiOnlySupported) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_wifi_only)) },
                    supportingContent = { Text(stringResource(Res.string.settings_wifi_only_body)) },
                    leadingContent = { Icon(painterResource(ReliquaryIcons.Downloads), contentDescription = null) },
                    trailingContent = { Switch(checked = wifiOnly, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = wifiOnly,
                        role = Role.Switch,
                        onValueChange = viewModel::setWifiOnlyDownloads,
                    ),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_storage_limit)) },
                supportingContent = {
                    Text(
                        storageLimit?.let { stringResource(Res.string.settings_storage_limit_body, fileSizeText(it)) }
                            ?: stringResource(Res.string.settings_storage_limit_body_none),
                    )
                },
                leadingContent = { Icon(painterResource(ReliquaryIcons.Storage), contentDescription = null) },
                trailingContent = { Text(storageLimitLabel(storageLimit), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.clickable { choosingLimit = true },
            )

            SectionTitle(stringResource(Res.string.settings_section_playback))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_skip_sponsors)) },
                supportingContent = { Text(stringResource(Res.string.settings_skip_sponsors_body)) },
                leadingContent = { Icon(painterResource(ReliquaryIcons.Skip), contentDescription = null) },
                trailingContent = { Switch(checked = skipSponsors, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = skipSponsors,
                    role = Role.Switch,
                    onValueChange = viewModel::setSkipSponsorSegments,
                ),
            )

            SectionTitle(stringResource(Res.string.settings_section_account))
            account?.let { info ->
                ListItem(
                    headlineContent = { Text(info.server) },
                    supportingContent = {
                        Text(stringResource(Res.string.settings_server_version, info.serverVersion.orEmpty()).trim())
                    },
                    leadingContent = { Icon(painterResource(ReliquaryIcons.Server), contentDescription = null) },
                )
                ListItem(
                    headlineContent = { Text(info.username ?: stringResource(Res.string.settings_unknown_user)) },
                    supportingContent = {
                        Text(
                            stringResource(if (info.usesToken) Res.string.settings_signed_in_token else Res.string.settings_signed_in_password),
                        )
                    },
                    leadingContent = { Icon(painterResource(ReliquaryIcons.Person), contentDescription = null) },
                )
            }
            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(painterResource(ReliquaryIcons.Logout), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(Res.string.settings_sign_out))
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }

    if (choosingLimit) {
        StorageLimitDialog(
            selected = storageLimit,
            onSelect = {
                viewModel.setStorageLimit(it)
                choosingLimit = false
            },
            onDismiss = { choosingLimit = false },
        )
    }
}

@Composable
private fun ThemeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ThemeMode.System -> Res.string.settings_theme_system
                            ThemeMode.Light -> Res.string.settings_theme_light
                            ThemeMode.Dark -> Res.string.settings_theme_dark
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun storageLimitLabel(bytes: Long?): String =
    bytes?.let { stringResource(Res.string.settings_storage_limit_gb, (it / BYTES_PER_GB).toInt()) }
        ?: stringResource(Res.string.settings_storage_limit_none)

/** No limit or one of the presets; picking one applies it. */
@Composable
private fun StorageLimitDialog(selected: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    val choices = listOf<Long?>(null) + StorageLimitPresetsGb.map { it * BYTES_PER_GB }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_storage_limit)) },
        text = {
            Column(Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onSelect(choice) }),
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(storageLimitLabel(choice), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_storage_limit_cancel)) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .semantics { heading() },
    )
}
