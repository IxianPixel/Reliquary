package com.malgra.reliquary.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.StatusBarScrim
import com.malgra.reliquary.core.ui.viewmodel.injectedViewModel
import com.malgra.reliquary.core.ui.wifiOnlySupported
import com.malgra.reliquary.feature.settings.resources.Res
import com.malgra.reliquary.feature.settings.resources.settings_section_account
import com.malgra.reliquary.feature.settings.resources.settings_section_downloads
import com.malgra.reliquary.feature.settings.resources.settings_section_playback
import com.malgra.reliquary.feature.settings.resources.settings_server_version
import com.malgra.reliquary.feature.settings.resources.settings_sign_out
import com.malgra.reliquary.feature.settings.resources.settings_signed_in_password
import com.malgra.reliquary.feature.settings.resources.settings_signed_in_token
import com.malgra.reliquary.feature.settings.resources.settings_skip_sponsors
import com.malgra.reliquary.feature.settings.resources.settings_skip_sponsors_body
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

            if (wifiOnlySupported) {
                SectionTitle(stringResource(Res.string.settings_section_downloads))
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
