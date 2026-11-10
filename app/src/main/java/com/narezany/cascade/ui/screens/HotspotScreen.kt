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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narezany.cascade.CascadeViewModel
import com.narezany.cascade.hotspot.ShizziBridge
import com.narezany.cascade.shizuku.ShizukuBridge
import com.narezany.cascade.ui.CascadeCard
import com.narezany.cascade.ui.InfoNote
import com.narezany.cascade.ui.ScreenHeader
import com.narezany.cascade.ui.SectionTitle
import com.narezany.cascade.ui.StatusPill
import com.narezany.cascade.ui.SwitchRow
import com.narezany.cascade.util.Format

@Composable
fun HotspotScreen(vm: CascadeViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shizzi by ShizziBridge.state.collectAsStateWithLifecycle()
    val shizuku by ShizukuBridge.state.collectAsStateWithLifecycle()
    var token by remember(settings.shizziToken) { mutableStateOf(settings.shizziToken) }

    DisposableEffect(Unit) {
        ShizziBridge.attach(context)
        onDispose { ShizziBridge.detach() }
    }
    LaunchedEffect(Unit) { ShizukuBridge.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Точка доступа",
            subtitle = "Shizzi + Shizuku",
            onBack = onBack,
        )

        InfoNote(
            text = "Cascade не встраивает Shizzi и не копирует его код. Приложение только отправляет публичные broadcast-команды установленному Shizzi, который сам включает и выключает точку доступа.",
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Shizzi")
        CascadeCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Статус", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            !shizzi.installed -> "Shizzi не установлен"
                            shizzi.loading -> "Выполняется…"
                            shizzi.connected -> "Точка доступа активна"
                            else -> shizzi.status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = if (shizzi.connected) "Активна" else "Выкл",
                    active = shizzi.connected,
                )
            }
            if (shizzi.clients > 0 || shizzi.up > 0 || shizzi.down > 0) {
                Text(
                    "Клиентов: ${shizzi.clients} · ↑ ${Format.bytes(shizzi.up)} · ↓ ${Format.bytes(shizzi.down)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (shizzi.error.isNotBlank()) {
                Text(
                    shizzi.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { ShizziBridge.start() },
                    enabled = shizzi.installed && token.isNotBlank() && !shizzi.loading && !shizzi.connected,
                    modifier = Modifier.weight(1f),
                ) {
                    if (shizzi.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                    Text(" Включить")
                }
                OutlinedButton(
                    onClick = { ShizziBridge.stop() },
                    enabled = shizzi.installed && shizzi.connected,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Выключить")
                }
                OutlinedButton(
                    onClick = { ShizziBridge.refresh() },
                    enabled = shizzi.installed,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", modifier = Modifier.size(17.dp))
                }
            }
            if (!shizzi.installed) {
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/carlelieser/shizzi/releases/latest") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Скачать Shizzi")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Токен автоматизации")
        CascadeCard {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.trim() },
                label = { Text("Токен Shizzi") },
                placeholder = { Text("Из настроек Shizzi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Нужен для отправки команд через AutomationReceiver") },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        ShizziBridge.saveToken(token)
                        vm.notify("Токен сохранён")
                    },
                    enabled = token.isNotBlank(),
                ) {
                    Text("Сохранить токен")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        CascadeCard(contentPadding = 8.dp) {
            SwitchRow(
                icon = Icons.Rounded.WifiTethering,
                title = "Запускать вместе с VPN",
                subtitle = "При подключении отправлять Shizzi команду START, при отключении — STOP",
                checked = settings.hotspotEnabled,
                onCheckedChange = { checked ->
                    vm.updateSettings({ it.copy(hotspotEnabled = checked) })
                    if (vm.engine.value.status == com.narezany.cascade.data.EngineStatus.CONNECTED) {
                        com.narezany.cascade.engine.CascadeVpnService.syncHotspot(context)
                    }
                    if (checked) vm.notify("Точка доступа будет запускаться при подключении VPN")
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Shizuku")
        CascadeCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Посредник shell", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            !shizuku.installed -> "Shizuku не установлен"
                            !shizuku.running -> "Shizuku не запущен"
                            !shizuku.permission -> "Нет разрешения"
                            shizuku.ready -> "Готово"
                            else -> "Подключение…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(text = if (shizuku.ready) "Готово" else "Нет", active = shizuku.ready)
            }
            if (shizuku.error.isNotBlank()) {
                Text(
                    shizuku.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { ShizukuBridge.requestPermission() },
                    enabled = shizuku.installed && shizuku.running && !shizuku.permission,
                ) {
                    Text("Разрешить")
                }
                OutlinedButton(onClick = { ShizukuBridge.openShizuku(context) }) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Открыть")
                }
            }
            SwitchRow(
                icon = Icons.Rounded.CheckCircle,
                title = "Использовать Shizuku для команд",
                subtitle = "Полезно, если Android блокирует broadcast между приложениями",
                checked = settings.useShizuku,
                onCheckedChange = { checked ->
                    vm.updateSettings { it.copy(useShizuku = checked) }
                    ShizukuBridge.refresh()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Точка доступа и VPN — разные вещи. Shizzi включает раздачу интернета, а проходит ли трафик клиентов через туннель Cascade, зависит от прошивки и политики туннелирования. Проверьте на своём устройстве.",
            icon = Icons.Rounded.WifiTethering,
            tone = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(24.dp))
    }
}
