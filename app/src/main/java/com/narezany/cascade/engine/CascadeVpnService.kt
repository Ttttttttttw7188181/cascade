package com.narezany.cascade.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.narezany.cascade.BuildConfig
import com.narezany.cascade.MainActivity
import com.narezany.cascade.R
import com.narezany.cascade.data.CascadeStore
import com.narezany.cascade.data.EngineStatus
import com.narezany.cascade.hotspot.ShizziBridge
import com.narezany.cascade.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the Android TUN interface and the Xray controller for one session.
 *
 * The service deliberately keeps all state in [CascadeRuntime], in the same process as
 * the UI. That makes live speed and session totals immediate, without a custom binder.
 */
class CascadeVpnService : VpnService() {
    companion object {
        private const val CHANNEL_ID = "cascade_vpn"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.narezany.cascade.action.START"
        private const val ACTION_STOP = "com.narezany.cascade.action.STOP"
        private const val ACTION_RELOAD = "com.narezany.cascade.action.RELOAD"
        private const val ACTION_HOTSPOT = "com.narezany.cascade.action.HOTSPOT"

        /** Applies the current hotspotEnabled setting to a running session. */
        fun syncHotspot(context: Context) {
            val intent = Intent(context, CascadeVpnService::class.java).setAction(ACTION_HOTSPOT)
            runCatching { context.startService(intent) }
        }

        fun start(context: Context, profileId: String) {
            CascadeStore.selectProfile(profileId)
            val intent = Intent(context, CascadeVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CascadeVpnService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun reload(context: Context) {
            val intent = Intent(context, CascadeVpnService::class.java).setAction(ACTION_RELOAD)
            runCatching { context.startService(intent) }
        }

        fun prepare(context: Context): Intent? = VpnService.prepare(context)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tun: ParcelFileDescriptor? = null
    private var trafficJob: Job? = null
    private var reloadJob: Job? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var running = false
    private var stopping = false
    private var hotspotStarted = false

    override fun onCreate() {
        super.onCreate()
        CascadeStore.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                reload()
                return START_STICKY
            }
            ACTION_HOTSPOT -> {
                syncHotspot()
                return if (running) START_STICKY else START_NOT_STICKY
            }
            else -> startSession()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopHotspot()
        trafficJob?.cancel()
        reloadJob?.cancel()
        unregisterNetwork()
        runCatching { tun?.close() }
        tun = null
        if (running) {
            runCatching { kotlinx.coroutines.runBlocking { XrayEngine.stop() } }
            running = false
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startSession() {
        if (running || stopping) return
        val settings = CascadeStore.settingsSnapshot()
        val profile = CascadeStore.selectedProfile()
        if (profile == null) {
            CascadeRuntime.failed("Сначала добавьте сервер")
            stopSelf()
            return
        }
        CascadeRuntime.starting(profile.id, profile.name)
        startForegroundCompat(buildNotification(profile.name, "", ""))

        if (prepare(this) != null) {
            failAndStop("Нет разрешения VPN")
            return
        }

        val config = runCatching {
            XrayConfigBuilder(settings, CascadeStore.profiles.value).build(profile.id)
        }.getOrElse {
            failAndStop(it.message ?: "Не удалось собрать конфигурацию")
            return
        }

        val fd = runCatching { establishTun(settings) }.getOrElse {
            failAndStop(it.message ?: "Не удалось создать VPN-интерфейс")
            return
        }
        tun = fd

        scope.launch {
            runCatching {
                XrayEngine.start(applicationContext, config.json, fd.fd)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    if (stopping) return@withContext
                    running = true
                    CascadeRuntime.connected(profile.id, profile.name, profile.lastPingMs)
                    startTrafficLoop()
                    registerNetwork(settings.failoverEnabled)
                    if (settings.hotspotEnabled) startHotspot()
                    updateNotification()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    failAndStop(error.message ?: "Xray не запустился")
                }
            }
        }
    }

    private fun establishTun(settings: com.narezany.cascade.data.AppSettings): ParcelFileDescriptor {
        val builder = Builder()
        builder.setSession("Cascade")
        builder.setMtu(settings.mtu)
        builder.addAddress("10.10.10.1", 30)
        builder.addRoute("0.0.0.0", 0)
        if (settings.ipv6) {
            builder.addAddress("fd00::1", 126)
            builder.addRoute("::", 0)
        }
        when (settings.dnsMode) {
            "google" -> {
                builder.addDnsServer("8.8.8.8")
                builder.addDnsServer("8.8.4.4")
                if (settings.ipv6) builder.addDnsServer("2001:4860:4860::8888")
            }
            "system" -> Unit
            else -> {
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("1.0.0.1")
                if (settings.ipv6) builder.addDnsServer("2606:4700:4700::1111")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val self = packageName
        if (settings.perAppEnabled && settings.perAppPackages.isNotEmpty()) {
            if (settings.perAppBypass) {
                settings.perAppPackages.forEach { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }
                runCatching { builder.addDisallowedApplication(self) }
            } else {
                settings.perAppPackages.forEach { pkg ->
                    if (pkg != self) runCatching { builder.addAllowedApplication(pkg) }
                }
            }
        } else {
            runCatching { builder.addDisallowedApplication(self) }
        }
        return builder.establish() ?: error("VPN-интерфейс не создан")
    }

    private fun startTrafficLoop() {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                val traffic = XrayEngine.queryTraffic()
                CascadeRuntime.updateTraffic(
                    up = traffic.up,
                    down = traffic.down,
                    upSpeed = traffic.up,
                    downSpeed = traffic.down,
                )
                if (++tick % 5 == 0) {
                    withContext(Dispatchers.Main) { updateNotification() }
                }
            }
        }
    }

    private fun registerNetwork(failover: Boolean) {
        if (networkCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivity = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                if (failover) scheduleReload()
            }

            override fun onLost(network: Network) {
                runCatching { setUnderlyingNetworks(null) }
                if (failover) scheduleReload()
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        networkCallback = callback
    }

    private fun unregisterNetwork() {
        val callback = networkCallback ?: return
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        networkCallback = null
        connectivity = null
    }

    private fun scheduleReload() {
        if (stopping || !running) return
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(2_500)
            withContext(Dispatchers.Main) { reload() }
        }
    }

    private fun reload() {
        if (!running || stopping) return
        val settings = CascadeStore.settingsSnapshot()
        val profile = CascadeStore.selectedProfile() ?: return
        val descriptor = tun ?: return
        val config = runCatching {
            XrayConfigBuilder(settings, CascadeStore.profiles.value).build(profile.id)
        }.getOrElse {
            failAndStop(it.message ?: "Не удалось пересобрать конфигурацию")
            return
        }
        scope.launch {
            runCatching {
                XrayEngine.stop()
                XrayEngine.start(applicationContext, config.json, descriptor.fd)
            }.onSuccess {
                CascadeRuntime.refreshed(profile.id, profile.name, profile.lastPingMs)
                withContext(Dispatchers.Main) { updateNotification() }
            }.onFailure {
                withContext(Dispatchers.Main) { failAndStop(it.message ?: "Не удалось перезапустить Xray") }
            }
        }
    }

    // Shizzi needs the VPN to be up before it picks the TUN as tethering upstream,
    // so START is sent only after Xray reports success, and STOP before teardown.
    private fun startHotspot() {
        if (hotspotStarted) return
        ShizziBridge.attach(applicationContext)
        ShizziBridge.start()
        hotspotStarted = true
    }

    private fun stopHotspot() {
        if (!hotspotStarted) return
        hotspotStarted = false
        ShizziBridge.stop()
        ShizziBridge.detach()
    }

    private fun syncHotspot() {
        if (!running || stopping) {
            // Started only to deliver this intent: no session is starting or running.
            if (tun == null && !stopping) stopSelf()
            return
        }
        if (CascadeStore.settingsSnapshot().hotspotEnabled) startHotspot() else stopHotspot()
    }

    private fun stopEverything() {
        if (stopping) return
        stopping = true
        stopHotspot()
        CascadeRuntime.stopping()
        trafficJob?.cancel()
        trafficJob = null
        reloadJob?.cancel()
        reloadJob = null
        unregisterNetwork()
        running = false
        runCatching { tun?.close() }
        tun = null
        scope.launch {
            XrayEngine.stop()
            withContext(Dispatchers.Main) {
                CascadeRuntime.stopped()
                ServiceCompat.stopForeground(this@CascadeVpnService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                NotificationManagerCompatCancel()
                stopSelf()
            }
        }
    }

    private fun failAndStop(message: String) {
        stopHotspot()
        CascadeRuntime.failed(message)
        running = false
        runCatching { tun?.close() }
        tun = null
        trafficJob?.cancel()
        trafficJob = null
        unregisterNetwork()
        scope.launch { XrayEngine.stop() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompatCancel()
        stopSelf()
    }

    private fun updateNotification() {
        val state = CascadeRuntime.engine.value
        val notification = buildNotification(
            state.profileName.ifBlank { "Cascade" },
            Format.speed(state.downSpeed),
            Format.speed(state.upSpeed),
        )
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(name: String, down: String, up: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, CascadeVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (down.isBlank() && up.isBlank()) {
            "Защищённое соединение активно"
        } else {
            "↓ $down   ↑ $up"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cascade)
            .setContentTitle(name)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Отключить", stop)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }.onFailure {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN-соединение",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Состояние и скорость Cascade VPN"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun NotificationManagerCompatCancel() {
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }
}
