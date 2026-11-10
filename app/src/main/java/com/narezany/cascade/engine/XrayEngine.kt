package com.narezany.cascade.engine

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin, app-owned bridge to the AndroidLibXrayLite controller. The VPN service is the
 * only caller that starts or stops the core; screens read state from [CascadeRuntime].
 */
object XrayEngine {
    private const val TAG = "CascadeEngine"

    private val initialized = AtomicBoolean(false)
    private var controller: CoreController? = null
    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long {
            Log.i(TAG, "core started")
            return 0
        }

        override fun shutdown(): Long {
            Log.i(TAG, "core stopped")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.i(TAG, "core status $code: ${message.orEmpty()}")
            return 0
        }
    }

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            Seq.setContext(context.applicationContext)
            Libv2ray.initCoreEnv("", deviceKey(context))
        }.onFailure {
            initialized.set(false)
            Log.e(TAG, "init failed", it)
            throw it
        }
    }

    val isRunning: Boolean get() = controller?.isRunning == true

    val version: String
        get() = runCatching { Libv2ray.checkVersionX() }.getOrDefault("Xray")

    suspend fun start(context: Context, config: String, tunFd: Int) = withContext(Dispatchers.IO) {
        init(context)
        val instance = controller ?: Libv2ray.newCoreController(callback).also { controller = it }
        instance.startLoop(config, tunFd)
        if (!instance.isRunning) error("Ядро Xray не запустилось")
        Unit
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching { controller?.stopLoop() }
        controller = null
        Unit
    }

    data class Traffic(
        val up: Long = 0L,
        val down: Long = 0L,
        val total: Long = 0L,
    )

    fun queryTraffic(): Traffic {
        val running = controller ?: return Traffic()
        if (!running.isRunning) return Traffic()
        val payload = runCatching { running.queryAllOutboundTrafficStats() }.getOrDefault("")
        var up = 0L
        var down = 0L
        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach
            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach
            val value = parts[2].toLongOrNull() ?: return@forEach
            when (parts[1].lowercase()) {
                "uplink", "up" -> up += value
                "downlink", "down" -> down += value
            }
        }
        return Traffic(up = up, down = down, total = up + down)
    }

    suspend fun measureDelay(context: Context, config: String, url: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            init(context)
            Libv2ray.measureOutboundDelay(config, url)
        }.getOrDefault(-1L)
    }

    private fun deviceKey(context: Context): String = runCatching {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .toByteArray(Charsets.UTF_8)
        Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING or Base64.URL_SAFE)
    }.getOrDefault("")
}
