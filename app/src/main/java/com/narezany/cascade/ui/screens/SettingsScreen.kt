package com.narezany.cascade.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narezany.cascade.BuildConfig
import com.narezany.cascade.CascadeViewModel
import com.narezany.cascade.data.EngineStatus
import com.narezany.cascade.data.RoutingPresets
import com.narezany.cascade.engine.CascadeVpnService
import com.narezany.cascade.ui.ScreenHeader
import com.narezany.cascade.ui.SectionTitle
import com.narezany.cascade.ui.SettingDivider
import com.narezany.cascade.ui.SettingRow
import com.narezany.cascade.ui.SettingsGroup
import com.narezany.cascade.ui.theme.AccentPalettes
import com.narezany.cascade.util.Format

@Composable
fun SettingsScreen(
    vm: CascadeViewModel,
    onOpenSubscriptions: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenFragmentation: () -> Unit,
    onOpenAggregation: () -> Unit,
    onOpenHotspot: () -> Unit,
    onOpenPerApp: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()

    val routingTitle = RoutingPresets.firstOrNull { it.id == settings.routingMode }?.title ?: "Свои правила"
    val themeTitle = when (settings.themeMode) {
        "light" -> "Светлая"
        "dark" -> "Тёмная"
        else -> "Системная"
    }
    val accentTitle = AccentPalettes.firstOrNull { it.id == settings.accent }?.title ?: "Океан"
    val perAppTitle = when {
        !settings.perAppEnabled -> "Все приложения"
        settings.perAppBypass -> "Кроме ${settings.perAppPackages.size}"
        else -> "Только ${settings.perAppPackages.size}"
    }
    val lastUpdate = subscriptions.maxOfOrNull { it.updatedAt } ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Настройки",
            subtitle = "${profiles.size} профилей · Cascade ${BuildConfig.VERSION_NAME}",
        )

        SectionTitle("Соединение")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Route,
                title = "Маршрутизация",
                subtitle = "Какие адреса идут напрямую",
                value = routingTitle,
                onClick = onOpenRouting,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.CallSplit,
                title = "Фрагментация",
                subtitle = "Дробление TLS ClientHello",
                value = if (settings.fragmentEnabled) "Вкл" else "Выкл",
                onClick = onOpenFragmentation,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.MergeType,
                title = "Агрегация",
                subtitle = "Внешний SOCKS5-балансировщик",
                value = if (settings.aggregationEnabled) "${settings.aggregationPort}" else "Выкл",
                onClick = onOpenAggregation,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.WifiTethering,
                title = "Точка доступа",
                subtitle = "Раздача VPN через Shizzi",
                value = "Shizzi",
                onClick = onOpenHotspot,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.Apps,
                title = "Приложения",
                subtitle = "Какие приложения используют VPN",
                value = perAppTitle,
                onClick = onOpenPerApp,
            )
            if (engine.status == EngineStatus.CONNECTED || engine.status == EngineStatus.STARTING) {
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "Отключить VPN",
                    subtitle = "Завершить текущую сессию",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { CascadeVpnService.stop(context) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Подписки")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Cloud,
                title = "Подписки",
                subtitle = if (subscriptions.isEmpty()) "Пока не добавлены" else "Обновлено ${Format.relativeTime(lastUpdate)}",
                value = subscriptions.size.toString(),
                onClick = onOpenSubscriptions,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.Refresh,
                title = "Обновить все подписки",
                subtitle = "Загрузить свежий список серверов",
                onClick = { vm.updateAllSubscriptions() },
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Интерфейс")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Palette,
                title = "Оформление",
                subtitle = "Тема, динамические цвета, акцент",
                value = "$themeTitle · $accentTitle",
                onClick = onOpenAppearance,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Данные")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Backup,
                title = "Резервная копия",
                subtitle = "Экспорт и импорт профилей",
                onClick = onOpenBackup,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("О приложении")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "О Cascade и авторах",
                subtitle = "Лицензии и благодарности",
                value = BuildConfig.VERSION_NAME,
                onClick = onOpenAbout,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Cascade — независимый клиент. Движок Xray используется по открытой лицензии.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
