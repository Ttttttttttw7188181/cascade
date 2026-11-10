package com.narezany.cascade.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import com.narezany.cascade.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Optional Shizuku bridge. Cascade never asks for root: Shizuku runs the user service
 * with the same shell identity that ADB uses, which is enough for Shizzi broadcasts.
 */
object ShizukuBridge {
    private const val REQUEST_CODE = 4242
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    data class State(
        val installed: Boolean = false,
        val running: Boolean = false,
        val permission: Boolean = false,
        val connected: Boolean = false,
        val error: String = "",
    ) {
        val ready: Boolean get() = permission && connected
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var appContext: Context? = null
    private var userService: IShizukuUserService? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var bound = false
    private var listenersRegistered = false

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh(rebind = true)
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        userService = null
        bound = false
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        refresh(rebind = true)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!listenersRegistered) {
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addBinderDeadListener(binderDead)
                Shizuku.addRequestPermissionResultListener(permissionResult)
                listenersRegistered = true
            }
        }
        refresh(rebind = true)
    }

    fun refresh(rebind: Boolean = false) {
        val context = appContext
        val installed = context != null && isInstalled(context)
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = if (running) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else {
            false
        }
        _state.value = State(
            installed = installed,
            running = running,
            permission = permission,
            connected = userService != null,
            error = _state.value.error,
        )
        if (rebind && running && permission && !bound) bind(context)
    }

    fun requestPermission() {
        val state = _state.value
        if (!state.installed) {
            _state.value = state.copy(error = "Shizuku не установлен")
            return
        }
        if (!state.running) {
            _state.value = state.copy(error = "Shizuku не запущен")
            return
        }
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { _state.value = _state.value.copy(error = it.message.orEmpty()) }
    }

    fun openShizuku(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    suspend fun run(command: String): String = withContext(Dispatchers.IO) {
        val service = userService
        if (service == null) {
            refresh(rebind = true)
            return@withContext "error: Shizuku не готов"
        }
        runCatching { service.run(command) }.getOrElse { "error: ${it.message}" }
    }

    private fun bind(context: Context?) {
        if (context == null || bound) return
        bound = true
        val component = ComponentName(context, ShizukuUserService::class.java)
        val serviceArgs = Shizuku.UserServiceArgs(component)
            .daemon(false)
            .processNameSuffix("shizuku")
            .version(1)
            .debuggable(BuildConfig.DEBUG)
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                userService = IShizukuUserService.Stub.asInterface(binder)
                _state.value = _state.value.copy(connected = userService != null, error = "")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                userService = null
                bound = false
                _state.value = _state.value.copy(connected = false)
            }
        }
        args = serviceArgs
        connection = serviceConnection
        runCatching {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        }.onFailure {
            bound = false
            _state.value = _state.value.copy(error = it.message.orEmpty())
        }
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
