package com.v2ray.ang.ui.hotspot

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.util.Utils

class HotspotActivity : BaseComponentActivity() {

    private val viewModel: HotspotViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        HotspotScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
private fun HotspotScreen(viewModel: HotspotViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tokenMissing by viewModel.tokenMissing.collectAsStateWithLifecycle()
    val switchLabel = stringResource(R.string.hotspot_enable)
    var token by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.token) {
        if (token.isBlank()) token = state.token
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.hotspot_title),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.hotspot_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (state.connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hotspot_status),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    when (state.status) {
                                        "CONNECTED" -> R.string.hotspot_status_connected
                                        "LOADING" -> R.string.hotspot_status_loading
                                        "READY" -> R.string.hotspot_status_ready
                                        "ERROR" -> R.string.hotspot_status_error
                                        else -> R.string.hotspot_status_unknown
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = state.connected || state.loading,
                            onCheckedChange = viewModel::setEnabled,
                            enabled = state.canControl && !state.loading,
                            modifier = Modifier.semantics {
                                contentDescription = switchLabel
                            }
                        )
                    }
                    state.detail?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.clientCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.hotspot_clients, state.clientCount),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state.bytesUp + state.bytesDown > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.hotspot_traffic,
                                state.bytesUp.toTrafficString(),
                                state.bytesDown.toTrafficString()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val message = when {
                tokenMissing -> stringResource(R.string.hotspot_error_token)
                state.lastError == "no_token" -> stringResource(R.string.hotspot_error_token)
                state.lastError == "not_installed" -> stringResource(R.string.hotspot_error_install)
                state.lastError == "timeout" -> stringResource(R.string.hotspot_error_timeout)
                !state.lastError.isNullOrBlank() -> stringResource(R.string.hotspot_error_refused)
                else -> null
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!state.installed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.hotspot_not_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { Utils.openUri(context, AppConfig.SHIZZI_RELEASES_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hotspot_install))
                }
            } else {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.hotspot_token)) },
                    supportingText = { Text(stringResource(R.string.hotspot_token_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.saveToken(token) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.hotspot_save))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = context.packageManager
                                .getLaunchIntentForPackage(AppConfig.SHIZZI_PACKAGE)
                            if (intent != null) {
                                context.startActivity(
                                    Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                Utils.openUri(context, AppConfig.SHIZZI_URL)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.hotspot_open))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.hotspot_requires),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = viewModel::refresh) {
                Text(stringResource(R.string.hotspot_refresh))
            }
            Text(
                text = stringResource(R.string.hotspot_credits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { Utils.openUri(context, AppConfig.SHIZZI_URL) }) {
                Text(stringResource(R.string.hotspot_shizzi_github))
            }
            NavigationBarsSpacer()
        }
    }
}
