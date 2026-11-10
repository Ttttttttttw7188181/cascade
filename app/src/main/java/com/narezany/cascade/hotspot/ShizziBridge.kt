package com.narezany.cascade.hotspot

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.narezany.cascade.data.CascadeStore
import com.narezany.cascade.shizuku.ShizukuBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bridge to Shizzi (https://github.com/carlelieser/shizzi).
 *
 * Cascade does not bundle or copy Shizzi. It only asks the installed automation
 * receiver to start or stop a tethering session, either with a normal broadcast or,
 * when Shizuku is available, with a shell-identity broadcast.
 */
object ShizziBridge {
    private const val PACKAGE = "dev.shizzi"
    private const val RECEIVER = "dev.shizzi.AutomationReceiver"
    private const val ACTION_START = "dev.shizzi.action.START"
    private const val ACTION_STOP = "dev.shizzi.action.STOP"
    private const val ACTION_TOGGLE = "dev.shizzi.action.TOGGLE"
    private const val ACTION_QUERY = "dev.shizzi.action.QUERY_STATUS"
    private const val ACTION_RESULT = "dev.shizzi.action.SESSION_RESULT"
    private const val TIMEOUT_MS = 15_000L
    private val TOKEN = Regex("^[A-Za-z0-9._~-]{1,256}$")

    data class State(
        val installed: Boolean = false,
        val token: String = "",
        val status: String = "UNKNOWN",
        val active: Boolean = false,
        val detail: String = "",
        val error: String = "",
        val pending: Boolean = false,
        val clients: Int = 0,
        val up: Long = 0L,
        val down: Long = 0L,
    ) {
        val connected: Boolean get() = active || status == "CONNECTED"
        val loading: Boolean get() = pending || status == "LOADING"
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timeoutJob: Job? = null
    private var context: Context? = null
    private var refCount = 0
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val data = intent ?: return
            timeoutJob?.cancel()
            timeoutJob = null
            val accepted = data.getBooleanExtra("accepted", true)
            val refusal = data.getStringExtra("refusal")
            val error = data.getStringExtra("error")
            val status = data.getStringExtra("status").orEmpty().ifBlank { "UNKNOWN" }
            _state.update {
                it.copy(
                    status = status,
                    active = data.getBooleanExtra("isActive", false),
                    detail = data.getStringExtra("detail").orEmpty(),
                    error = when {
                        !accepted -> refusal ?: "Команда отклонена"
                        else -> error.orEmpty()
                    },
                    clients = data.getIntExtra("clientCount", 0),
                    up = data.getLongExtra("bytesUp", 0L),
                    down = data.getLongExtra("bytesDown", 0L),
                    pending = false,
                )
            }
        }
    }

    fun attach(ctx: Context) {
        context = ctx.applicationContext
        if (refCount == 0) {
            runCatching {
                ContextCompat.registerReceiver(
                    context!!,
                    receiver,
                    IntentFilter(ACTION_RESULT),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                registered = true
            }
        }
        refCount++
        refresh()
    }

    fun detach() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount == 0 && registered) {
            runCatching { context?.unregisterReceiver(receiver) }
            registered = false
        }
    }

    fun refresh() {
        val ctx = context ?: return
        val token = CascadeStore.settingsSnapshot().shizziToken
        val installed = isInstalled(ctx)
        _state.update { it.copy(installed = installed, token = token) }
        if (installed && token.isNotBlank() && !_state.value.pending) {
            send(ACTION_QUERY, pending = false)
        }
    }

    fun saveToken(value: String) {
        val token = value.trim()
        CascadeStore.updateSettings { it.copy(shizziToken = token) }
        _state.update { it.copy(token = token, error = "") }
        if (token.isNotBlank() && !_state.value.pending) {
            send(ACTION_QUERY, pending = false)
        }
    }

    fun start() = send(ACTION_START, pending = true)

    fun stop() = send(ACTION_STOP, pending = true)

    fun toggle() {
        if (_state.value.connected || _state.value.loading) stop() else start()
    }

    private fun send(action: String, pending: Boolean) {
        val ctx = context ?: return
        val state = _state.value
        if (!state.installed) {
            _state.update { it.copy(error = "Shizzi не установлен") }
            return
        }
        if (!TOKEN.matches(state.token)) {
            _state.update { it.copy(error = "Укажите токен Shizzi") }
            return
        }
        val useShizuku = CascadeStore.settingsSnapshot().useShizuku && ShizukuBridge.state.value.ready
        if (useShizuku) {
            val command = "am broadcast -a $action -n $PACKAGE/$RECEIVER --es token '${state.token}'"
            scope.launch {
                val result = ShizukuBridge.run(command)
                if (result.startsWith("error:")) {
                    _state.update { it.copy(error = result.removePrefix("error:").trim(), pending = false) }
                }
            }
        } else {
            val intent = Intent(action).apply {
                component = ComponentName(PACKAGE, RECEIVER)
                putExtra("token", state.token)
            }
            runCatching { ctx.sendBroadcast(intent) }
                .onFailure { _state.update { it.copy(error = it.message.orEmpty(), pending = false) } }
        }
        if (pending) {
            timeoutJob?.cancel()
            _state.update { it.copy(pending = true, error = "") }
            timeoutJob = scope.launch {
                delay(TIMEOUT_MS)
                _state.update {
                    if (it.pending) it.copy(pending = false, error = "Shizzi не ответил") else it
                }
            }
        }
    }

    private fun isInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
