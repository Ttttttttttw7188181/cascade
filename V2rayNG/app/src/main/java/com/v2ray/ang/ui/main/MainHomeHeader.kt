package com.v2ray.ang.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.TrafficMessage
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.hotspot.ShizziController
import java.util.Locale

private enum class HomeNetwork { NONE, WIFI, CELLULAR, VPN, OTHER }

@Composable
fun MainHomeHeader(
    isRunning: Boolean,
    serverName: String?,
    traffic: TrafficMessage,
    detailText: String?,
    aggregationEnabled: Boolean,
    hotspot: ShizziController.State,
    onToggle: () -> Unit,
    onAggregationClick: () -> Unit,
    onHotspotClick: () -> Unit,
    onHotspotToggle: (Boolean) -> Unit,
    onSubscriptionsClick: () -> Unit,
) {
    val network = rememberHomeNetwork()
    val hotspotToggleLabel = stringResource(R.string.home_action_hotspot_toggle)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        HeroCard(
            isRunning = isRunning,
            serverName = serverName,
            traffic = traffic,
            detailText = detailText,
            network = network,
            onToggle = onToggle
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                iconRes = R.drawable.ic_merge_24dp,
                title = stringResource(R.string.home_action_aggregation),
                subtitle = stringResource(
                    if (aggregationEnabled) R.string.home_action_aggregation_on
                    else R.string.home_action_aggregation_off
                ),
                highlighted = aggregationEnabled,
                onClick = onAggregationClick,
            )
            QuickActionCard(
                iconRes = R.drawable.ic_hotspot_24dp,
                title = stringResource(R.string.home_action_hotspot),
                subtitle = stringResource(
                    if (hotspot.connected) R.string.home_action_hotspot_on
                    else R.string.home_action_hotspot_off
                ),
                highlighted = hotspot.connected,
                onClick = onHotspotClick,
                trailing = {
                    Switch(
                        checked = hotspot.connected || hotspot.loading,
                        onCheckedChange = onHotspotToggle,
                        enabled = hotspot.canControl,
                        modifier = Modifier
                            .scale(0.72f)
                            .semantics { contentDescription = hotspotToggleLabel }
                    )
                }
            )
            QuickActionCard(
                iconRes = R.drawable.ic_subscriptions_24dp,
                title = stringResource(R.string.home_action_subscriptions),
                subtitle = stringResource(R.string.home_action_subscriptions_hint),
                highlighted = false,
                onClick = onSubscriptionsClick,
            )
        }
    }
}

@Composable
private fun HeroCard(
    isRunning: Boolean,
    serverName: String?,
    traffic: TrafficMessage,
    detailText: String?,
    network: HomeNetwork,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(30.dp)
    val contentColor = if (isRunning) colors.onPrimary else colors.onSurface
    val brush = if (isRunning) {
        Brush.linearGradient(
            listOf(colors.primary, colors.primary, colors.tertiary.copy(alpha = 0.92f))
        )
    } else {
        Brush.linearGradient(
            listOf(colors.surfaceContainerHigh, colors.surfaceContainer)
        )
    }
    val pulse = rememberInfiniteTransition(label = "power-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "power-pulse-scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (isRunning) R.string.home_status_tap_stop else R.string.home_status_tap
                ),
                onClick = onToggle,
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (isRunning) Color(0xFF6EF2B0) else contentColor.copy(alpha = 0.6f),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (isRunning) R.string.home_status_connected
                            else R.string.home_status_disconnected
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            when (network) {
                                HomeNetwork.WIFI -> R.drawable.ic_wifi_24dp
                                HomeNetwork.CELLULAR -> R.drawable.ic_cell_24dp
                                HomeNetwork.VPN -> R.drawable.ic_lock_24dp
                                else -> R.drawable.ic_cell_24dp
                            }
                        ),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(
                            when (network) {
                                HomeNetwork.WIFI -> R.string.home_network_wifi
                                HomeNetwork.CELLULAR -> R.string.home_network_cellular
                                HomeNetwork.VPN -> R.string.home_network_vpn
                                HomeNetwork.OTHER -> R.string.home_network_other
                                HomeNetwork.NONE -> R.string.home_network_none
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_selected_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.72f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = serverName ?: stringResource(R.string.home_no_server),
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detailText ?: stringResource(
                        if (isRunning) R.string.home_status_tap_stop else R.string.home_status_tap
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(contentAlignment = Alignment.Center) {
                if (isRunning) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .background(contentColor.copy(alpha = 0.12f), CircleShape)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (isRunning) contentColor else colors.primary,
                    modifier = Modifier.size(66.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_power_24dp),
                            // The whole card is the button; its onClickLabel names the action.
                            contentDescription = null,
                            tint = if (isRunning) colors.primary else colors.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(contentColor.copy(alpha = 0.16f))
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrafficMetric(
                arrow = "↓",
                label = stringResource(R.string.home_traffic_down),
                value = if (isRunning) traffic.downSpeed.toSpeedString() else "—",
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            TrafficMetric(
                arrow = "↑",
                label = stringResource(R.string.home_traffic_up),
                value = if (isRunning) traffic.upSpeed.toSpeedString() else "—",
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            TrafficMetric(
                arrow = "⏱",
                label = stringResource(R.string.home_session),
                value = if (isRunning) formatDuration(traffic.elapsedSeconds) else "—",
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TrafficMetric(
    arrow: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = arrow, color = color.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.66f),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickActionCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (highlighted) colors.secondaryContainer else colors.surfaceContainerLow,
        modifier = Modifier.width(152.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (highlighted) colors.onSecondaryContainer else colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (highlighted) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlighted) colors.onSecondaryContainer.copy(alpha = 0.78f)
                    else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun rememberHomeNetwork(): HomeNetwork {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var kind by remember { mutableStateOf(readNetwork(manager)) }

    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                kind = readNetwork(manager)
            }

            override fun onLost(network: Network) {
                kind = readNetwork(manager)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                kind = readNetwork(manager)
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }
    return kind
}

private fun readNetwork(manager: ConnectivityManager?): HomeNetwork {
    val capabilities = manager?.let { service ->
        service.activeNetwork?.let { service.getNetworkCapabilities(it) }
    } ?: return HomeNetwork.NONE
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> HomeNetwork.VPN
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> HomeNetwork.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> HomeNetwork.CELLULAR
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> HomeNetwork.OTHER
        else -> HomeNetwork.NONE
    }
}

fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
