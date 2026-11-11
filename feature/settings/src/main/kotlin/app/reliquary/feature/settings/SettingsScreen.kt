package app.reliquary.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reliquary.core.designsystem.icon.ReliquaryIcons

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        account?.let { info ->
            ListItem(
                headlineContent = { Text(info.server) },
                supportingContent = { Text("Tube Archivist ${info.serverVersion ?: ""}".trim()) },
                leadingContent = { Icon(painterResource(ReliquaryIcons.Server), contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text(info.username ?: "Unknown user") },
                supportingContent = { Text(if (info.usesToken) "Signed in with an API token" else "Signed in with a password") },
                leadingContent = { Icon(painterResource(ReliquaryIcons.Person), contentDescription = null) },
            )
        }
        OutlinedButton(
            onClick = viewModel::signOut,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(ReliquaryIcons.Logout), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Sign out")
        }
    }
}
