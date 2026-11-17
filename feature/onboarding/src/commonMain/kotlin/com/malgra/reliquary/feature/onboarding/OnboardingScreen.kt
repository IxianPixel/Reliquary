@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.malgra.reliquary.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.designsystem.theme.ReliquaryTheme
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.ServerUrl
import com.malgra.reliquary.core.ui.LocalNetworkAccess
import com.malgra.reliquary.core.ui.LocalNetworkAccessCard
import com.malgra.reliquary.core.ui.rememberLocalNetworkAccess
import com.malgra.reliquary.core.ui.viewmodel.injectedViewModel
import com.malgra.reliquary.feature.onboarding.resources.Res
import com.malgra.reliquary.feature.onboarding.resources.onboarding_connect
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_credentials
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_http
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_no_cookie
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_other
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_refused
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_timeout
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_tls
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_token
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_unexpected
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_unknown_host
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_version
import com.malgra.reliquary.feature.onboarding.resources.onboarding_expired
import com.malgra.reliquary.feature.onboarding.resources.onboarding_footer
import com.malgra.reliquary.feature.onboarding.resources.onboarding_intro
import com.malgra.reliquary.feature.onboarding.resources.onboarding_method_password
import com.malgra.reliquary.feature.onboarding.resources.onboarding_method_token
import com.malgra.reliquary.feature.onboarding.resources.onboarding_password
import com.malgra.reliquary.feature.onboarding.resources.onboarding_password_help
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_checking
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_found
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_invalid
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_label
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_not_ta
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_placeholder
import com.malgra.reliquary.feature.onboarding.resources.onboarding_sign_in_with
import com.malgra.reliquary.feature.onboarding.resources.onboarding_title
import com.malgra.reliquary.feature.onboarding.resources.onboarding_token
import com.malgra.reliquary.feature.onboarding.resources.onboarding_token_help
import com.malgra.reliquary.feature.onboarding.resources.onboarding_username
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = injectedViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localNetwork = rememberLocalNetworkAccess(
        serverUrl = ServerUrl.normalize(state.serverUrl)?.toString(),
        onGranted = viewModel::recheckServer,
    )
    OnboardingScreen(
        state = state,
        localNetwork = localNetwork,
        onServerUrlChange = viewModel::onServerUrlChange,
        onMethodChange = viewModel::onMethodChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTokenChange = viewModel::onTokenChange,
        onConnect = viewModel::connect,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onServerUrlChange: (String) -> Unit,
    onMethodChange: (SignInMethod) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    localNetwork: LocalNetworkAccess? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(expired = state.sessionExpired)

        ServerField(state, onServerUrlChange)
        localNetwork?.let { LocalNetworkAccessCard(it) }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.onboarding_sign_in_with), style = MaterialTheme.typography.titleSmall)
            MethodPicker(selected = state.method, onSelect = onMethodChange)
            when (state.method) {
                SignInMethod.Password -> PasswordFields(state, onUsernameChange, onPasswordChange, onConnect)
                SignInMethod.Token -> TokenField(state, onTokenChange, onConnect)
            }
            state.error?.let { error ->
                Text(
                    text = signInErrorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onConnect,
                enabled = state.canConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (state.signingIn) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.onboarding_connect), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                stringResource(Res.string.onboarding_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Header(expired: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(ReliquaryIcons.Archive), contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            stringResource(Res.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(if (expired) Res.string.onboarding_expired else Res.string.onboarding_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerField(state: OnboardingUiState, onChange: (String) -> Unit) {
    val problem = when (val server = state.server) {
        ServerStatus.InvalidUrl -> stringResource(Res.string.onboarding_server_invalid)
        ServerStatus.NotTubeArchivist -> stringResource(Res.string.onboarding_server_not_ta)
        is ServerStatus.Unreachable -> failureText(server.failure)
        else -> null
    }
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.onboarding_server_label)) },
        placeholder = { Text(stringResource(Res.string.onboarding_server_placeholder)) },
        leadingIcon = { Icon(painterResource(ReliquaryIcons.Server), contentDescription = null) },
        isError = problem != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        supportingText = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when {
                    problem != null -> Text(problem)
                    state.server == ServerStatus.Checking -> Text(stringResource(Res.string.onboarding_server_checking))
                    state.server == ServerStatus.Found -> {
                        Icon(
                            painterResource(ReliquaryIcons.CheckCircle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(stringResource(Res.string.onboarding_server_found))
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MethodPicker(selected: SignInMethod, onSelect: (SignInMethod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        SignInMethod.entries.forEachIndexed { index, method ->
            ToggleButton(
                checked = method == selected,
                onCheckedChange = { onSelect(method) },
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        when (method) {
                            SignInMethod.Password -> Res.string.onboarding_method_password
                            SignInMethod.Token -> Res.string.onboarding_method_token
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PasswordFields(
    state: OnboardingUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(Res.string.onboarding_username)) },
            leadingIcon = { Icon(painterResource(ReliquaryIcons.Person), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(Res.string.onboarding_password)) },
            leadingIcon = { Icon(painterResource(ReliquaryIcons.Lock), contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            supportingText = { Text(stringResource(Res.string.onboarding_password_help)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TokenField(state: OnboardingUiState, onChange: (String) -> Unit, onDone: () -> Unit) {
    OutlinedTextField(
        value = state.token,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.onboarding_token)) },
        leadingIcon = { Icon(painterResource(ReliquaryIcons.Key), contentDescription = null) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        supportingText = { Text(stringResource(Res.string.onboarding_token_help)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun signInErrorText(error: SignInError): String = when (error) {
    SignInError.InvalidCredentials -> stringResource(Res.string.onboarding_error_credentials)
    SignInError.TokenRejected -> stringResource(Res.string.onboarding_error_token)
    is SignInError.UnsupportedVersion -> stringResource(Res.string.onboarding_error_version, error.version)
    is SignInError.Network -> failureText(error.failure)
}

@Composable
private fun failureText(failure: Failure): String = when (failure) {
    Failure.UnknownHost -> stringResource(Res.string.onboarding_error_unknown_host)
    Failure.ConnectionRefused -> stringResource(Res.string.onboarding_error_refused)
    Failure.Timeout -> stringResource(Res.string.onboarding_error_timeout)
    is Failure.Tls -> stringResource(Res.string.onboarding_error_tls, failure.message.orEmpty())
    is Failure.Http -> stringResource(Res.string.onboarding_error_http, failure.code)
    Failure.NoSessionCookie -> stringResource(Res.string.onboarding_error_no_cookie)
    Failure.UnexpectedResponse -> stringResource(Res.string.onboarding_error_unexpected)
    is Failure.Io -> stringResource(Res.string.onboarding_error_other, failure.message.orEmpty())
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    ReliquaryTheme {
        Surface {
            OnboardingScreen(
                state = OnboardingUiState(serverUrl = "https://archive.home.arpa", server = ServerStatus.Found, username = "alex"),
                onServerUrlChange = {}, onMethodChange = {}, onUsernameChange = {},
                onPasswordChange = {}, onTokenChange = {}, onConnect = {},
            )
        }
    }
}
