package com.v2ray.ang.ui.aggregation

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.util.Utils

class AggregationActivity : BaseComponentActivity() {

    private val viewModel: AggregationViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        AggregationScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
private fun AggregationScreen(viewModel: AggregationViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.aggregation_title),
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
                        text = stringResource(R.string.aggregation_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsSwitchItem(
                title = stringResource(R.string.aggregation_enable),
                summary = stringResource(R.string.aggregation_enable_summary),
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled
            )
            SettingsEditItem(
                title = stringResource(R.string.aggregation_host),
                value = state.host,
                onValueChanged = viewModel::setHost
            )
            SettingsEditItem(
                title = stringResource(R.string.aggregation_port),
                value = state.port,
                keyboardNumber = true,
                onValueChanged = viewModel::setPort
            )
            SettingsEditItem(
                title = stringResource(R.string.aggregation_username),
                value = state.username,
                onValueChanged = viewModel::setUsername
            )
            SettingsEditItem(
                title = stringResource(R.string.aggregation_password),
                value = state.password,
                isPassword = true,
                onValueChanged = viewModel::setPassword
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::checkConnection,
                enabled = !state.checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.aggregation_check))
                }
            }
            state.check?.let { check ->
                Spacer(Modifier.height(8.dp))
                val text = when (check) {
                    is AggregationCheck.Success -> stringResource(
                        R.string.aggregation_ok,
                        check.host,
                        check.port,
                    )

                    is AggregationCheck.Failure -> stringResource(
                        R.string.aggregation_fail,
                        check.host,
                        check.port,
                    )

                    AggregationCheck.InvalidHost ->
                        stringResource(R.string.aggregation_invalid_host)

                    AggregationCheck.InvalidPort ->
                        stringResource(R.string.aggregation_invalid_port)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (check is AggregationCheck.Success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.aggregation_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { Utils.openUri(context, AppConfig.NET_MERGE_URL) }) {
                Text(stringResource(R.string.aggregation_netmerge))
            }
            Text(
                text = stringResource(R.string.aggregation_credits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NavigationBarsSpacer()
        }
    }
}
