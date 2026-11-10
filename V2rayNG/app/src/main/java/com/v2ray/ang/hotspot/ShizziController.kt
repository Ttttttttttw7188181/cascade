package com.v2ray.ang.hotspot

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Small bridge to the Shizzi app (https://github.com/carlelieser/shizzi).
 *
 * Cascade does not bundle or copy Shizzi. When the user installs it and enables automation,
 * this controller starts and stops its tethering session through the documented broadcast API.
 * Shizzi keeps ownership of the privileged tethering work; Cascade only sends commands and
 * listens for the public status result.
 */
object ShizziController {

    data class State(
        val installed: Boolean = false,
        val token: String = "",
        val status: String = "UNKNOWN",
        val isActive: Boolean = false,
        val detail: String? = null,
        val lastError: String? = null,
        val clientCount: Int = 0,
        val bytesUp: Long = 0,
        val bytesDown: Long = 0,
        val pending: Boolean = false,
    ) {
        val connected: Boolean get() = isActive || status == "CONNECTED"
        val loading: Boolean get() = pending || status == "LOADING"
        val canControl: Boolean get() = installed && token.isNotBlank()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var appContext: Context? = null
    private var refCount = 0
    private var receiverRegistered = false

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val accepted = safeIntent.getBooleanExtra("accepted", true)
            val refusal = safeIntent.getStringExtra("refusal")
            val error = safeIntent.getStringExtra("error")
            val status = safeIntent.getStringExtra("status").orEmpty().ifBlank { "UNKNOWN" }
            _state.update {
                it.copy(
                    status = status,
                    isActive = safeIntent.getBooleanExtra("isActive", false),
                    detail = safeIntent.getStringExtra("detail"),
                    lastError = when {
                        !accepted -> refusal ?: "refused"
                        else -> error
                    },
                    clientCount = safeIntent.getIntExtra("clientCount", 0),
                    bytesUp = safeIntent.getLongExtra("bytesUp", 0L),
                    bytesDown = safeIntent.getLongExtra("bytesDown", 0L),
                    pending = false,
                )
            }
        }
    }

    /** Registers the status listener; every screen that shows the toggle calls this. */
    fun attach(context: Context) {
        appContext = context.applicationContext
        if (refCount == 0) {
            runCatching {
                ContextCompat.registerReceiver(
                    appContext!!,
                    resultReceiver,
                    IntentFilter(AppConfig.SHIZZI_ACTION_SESSION_RESULT),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiverRegistered = true
            }
        }
        refCount++
        refresh()
    }

    fun detach() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount == 0 && receiverRegistered) {
            runCatching { appContext?.unregisterReceiver(resultReceiver) }
            receiverRegistered = false
        }
    }

    fun refresh() {
        val context = appContext ?: return
        val installed = isInstalled(context)
        val token = MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZZI_TOKEN, "").orEmpty()
        _state.update { it.copy(installed = installed, token = token) }
        if (installed && token.isNotBlank()) {
            send(AppConfig.SHIZZI_ACTION_QUERY_STATUS, pending = false)
        }
    }

    fun saveToken(token: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_SHIZZI_TOKEN, token.trim())
        _state.update { it.copy(token = token.trim(), lastError = null) }
        if (token.isNotBlank()) {
            send(AppConfig.SHIZZI_ACTION_QUERY_STATUS, pending = false)
        }
    }

    fun start() = send(AppConfig.SHIZZI_ACTION_START, pending = true)

    fun stop() = send(AppConfig.SHIZZI_ACTION_STOP, pending = true)

    fun toggle() {
        if (_state.value.connected || _state.value.loading) stop() else start()
    }

    private fun send(action: String, pending: Boolean) {
        val context = appContext ?: return
        val current = _state.value
        if (!current.installed) {
            _state.update { it.copy(lastError = "not_installed") }
            return
        }
        if (current.token.isBlank()) {
            _state.update { it.copy(lastError = "no_token") }
            return
        }
        val intent = Intent(action).apply {
            component = ComponentName(
                AppConfig.SHIZZI_PACKAGE,
                AppConfig.SHIZZI_AUTOMATION_RECEIVER,
            )
            putExtra("token", current.token)
        }
        runCatching { context.sendBroadcast(intent) }
            .onFailure { _state.update { state -> state.copy(lastError = it.message) } }
        if (pending) {
            _state.update { it.copy(pending = true, lastError = null) }
        }
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(AppConfig.SHIZZI_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }
}
