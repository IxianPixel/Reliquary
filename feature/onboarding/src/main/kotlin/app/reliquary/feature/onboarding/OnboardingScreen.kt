@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.reliquary.feature.onboarding

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reliquary.core.designsystem.icon.ReliquaryIcons
import app.reliquary.core.designsystem.theme.ReliquaryTheme
import app.reliquary.core.network.Failure

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
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

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.onboarding_sign_in_with), style = MaterialTheme.typography.titleSmall)
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
                    Text(stringResource(R.string.onboarding_connect), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                stringResource(R.string.onboarding_footer),
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
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(if (expired) R.string.onboarding_expired else R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerField(state: OnboardingUiState, onChange: (String) -> Unit) {
    val problem = when (val server = state.server) {
        ServerStatus.InvalidUrl -> stringResource(R.string.onboarding_server_invalid)
        ServerStatus.NotTubeArchivist -> stringResource(R.string.onboarding_server_not_ta)
        is ServerStatus.Unreachable -> failureText(server.failure)
        else -> null
    }
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.onboarding_server_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_server_placeholder)) },
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
                    state.server == ServerStatus.Checking -> Text(stringResource(R.string.onboarding_server_checking))
                    state.server == ServerStatus.Found -> {
                        Icon(
                            painterResource(ReliquaryIcons.CheckCircle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(stringResource(R.string.onboarding_server_found))
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
                            SignInMethod.Password -> R.string.onboarding_method_password
                            SignInMethod.Token -> R.string.onboarding_method_token
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
            label = { Text(stringResource(R.string.onboarding_username)) },
            leadingIcon = { Icon(painterResource(ReliquaryIcons.Person), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.onboarding_password)) },
            leadingIcon = { Icon(painterResource(ReliquaryIcons.Lock), contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            supportingText = { Text(stringResource(R.string.onboarding_password_help)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TokenField(state: OnboardingUiState, onChange: (String) -> Unit, onDone: () -> Unit) {
    OutlinedTextField(
        value = state.token,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.onboarding_token)) },
        leadingIcon = { Icon(painterResource(ReliquaryIcons.Key), contentDescription = null) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        supportingText = { Text(stringResource(R.string.onboarding_token_help)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun signInErrorText(error: SignInError): String = when (error) {
    SignInError.InvalidCredentials -> stringResource(R.string.onboarding_error_credentials)
    SignInError.TokenRejected -> stringResource(R.string.onboarding_error_token)
    is SignInError.UnsupportedVersion -> stringResource(R.string.onboarding_error_version, error.version)
    is SignInError.Network -> failureText(error.failure)
}

@Composable
private fun failureText(failure: Failure): String = when (failure) {
    Failure.UnknownHost -> stringResource(R.string.onboarding_error_unknown_host)
    Failure.ConnectionRefused -> stringResource(R.string.onboarding_error_refused)
    Failure.Timeout -> stringResource(R.string.onboarding_error_timeout)
    is Failure.Tls -> stringResource(R.string.onboarding_error_tls, failure.message.orEmpty())
    is Failure.Http -> stringResource(R.string.onboarding_error_http, failure.code)
    Failure.NoSessionCookie -> stringResource(R.string.onboarding_error_no_cookie)
    Failure.UnexpectedResponse -> stringResource(R.string.onboarding_error_unexpected)
    is Failure.Io -> stringResource(R.string.onboarding_error_other, failure.message.orEmpty())
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
