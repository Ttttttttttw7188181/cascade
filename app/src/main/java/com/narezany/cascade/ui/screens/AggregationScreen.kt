package com.narezany.cascade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narezany.cascade.CascadeViewModel
import com.narezany.cascade.ui.CascadeCard
import com.narezany.cascade.ui.InfoNote
import com.narezany.cascade.ui.ScreenHeader
import com.narezany.cascade.ui.SectionTitle
import com.narezany.cascade.ui.SwitchRow

@Composable
fun AggregationScreen(vm: CascadeViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var host by remember(settings.aggregationHost) { mutableStateOf(settings.aggregationHost) }
    var port by remember(settings.aggregationPort) { mutableStateOf(settings.aggregationPort.toString()) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Агрегация",
            subtitle = "Внешний SOCKS5-балансировщик",
            onBack = onBack,
        )

        CascadeCard(contentPadding = 8.dp) {
            SwitchRow(
                icon = Icons.Rounded.MergeType,
                title = "Отправлять трафик в SOCKS5",
                subtitle = "Xray будет использовать внешний балансировщик как выход",
                checked = settings.aggregationEnabled,
                onCheckedChange = { checked ->
                    vm.updateSettings({ it.copy(aggregationEnabled = checked) }, reload = true)
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Адрес балансировщика")
        CascadeCard {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("Хост") },
                placeholder = { Text("127.0.0.1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                label = { Text("Порт SOCKS5") },
                placeholder = { Text("1080") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (error.isNotEmpty()) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { vm.testAggregation(host, port.toIntOrNull() ?: 0) },
                ) {
                    IconText(Icons.Rounded.Dns, "Проверить")
                }
                Button(
                    onClick = {
                        val portValue = port.toIntOrNull()
                        error = when {
                            host.isBlank() -> "Укажите хост"
                            portValue == null || portValue !in 1..65535 -> "Порт от 1 до 65535"
                            else -> ""
                        }
                        if (error.isEmpty()) {
                            vm.updateSettings({
                                it.copy(
                                    aggregationHost = host,
                                    aggregationPort = portValue ?: 1080,
                                )
                            }, reload = true)
                            vm.notify("Адрес агрегации сохранён")
                        }
                    },
                ) {
                    Text("Сохранить")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Android разрешает только один активный VPN. Поэтому net-merge/NetCombine запускается отдельно — на телефоне через Shizuku или Termux, либо на роутере — и слушает SOCKS5. Cascade отправляет трафик в этот порт, а балансировщик уже распределяет его по серверам.",
        )
        Spacer(Modifier.height(12.dp))
        InfoNote(
            text = "Проверка выше только убеждается, что порт отвечает как SOCKS5. Реальное распределение и здоровье серверов остаются на стороне net-merge.",
            icon = Icons.Rounded.MergeType,
            tone = MaterialTheme.colorScheme.tertiary,
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/net-merge/net-merge") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconText(Icons.Rounded.OpenInNew, "Проект net-merge на GitHub")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    androidx.compose.material3.Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 7.dp).size(17.dp),
    )
    Text(text)
}
