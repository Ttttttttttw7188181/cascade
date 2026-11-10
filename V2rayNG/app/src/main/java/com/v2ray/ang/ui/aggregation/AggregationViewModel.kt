package com.v2ray.ang.ui.aggregation

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Result of the optional balancer reachability probe. */
sealed interface AggregationCheck {
    data class Success(val host: String, val port: Int) : AggregationCheck
    data class Failure(val host: String, val port: Int) : AggregationCheck
    data object InvalidHost : AggregationCheck
    data object InvalidPort : AggregationCheck
}

data class AggregationUiState(
    val enabled: Boolean = false,
    val host: String = DEFAULT_HOST,
    val port: String = DEFAULT_PORT,
    val username: String = "",
    val password: String = "",
    val checking: Boolean = false,
    val check: AggregationCheck? = null,
) {
    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = "1080"
    }
}

/** Parses the port field; null means it is outside the valid TCP range. */
internal fun parseAggregationPort(value: String): Int? =
    value.toIntOrNull()?.takeIf { it in 1..65535 }

class AggregationViewModel(application: Application) : BaseViewModel(application) {

    private val _state = MutableStateFlow(
        AggregationUiState(
            enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_AGGREGATION_ENABLED, false),
            host = MmkvManager.decodeSettingsString(
                AppConfig.PREF_AGGREGATION_HOST,
                AggregationUiState.DEFAULT_HOST,
            ).orEmpty().ifBlank { AggregationUiState.DEFAULT_HOST },
            port = MmkvManager.decodeSettingsString(
                AppConfig.PREF_AGGREGATION_PORT,
                AggregationUiState.DEFAULT_PORT,
            ).orEmpty().ifBlank { AggregationUiState.DEFAULT_PORT },
            username = MmkvManager.decodeSettingsString(
                AppConfig.PREF_AGGREGATION_USERNAME,
                "",
            ).orEmpty(),
            password = MmkvManager.decodeSettingsString(
                AppConfig.PREF_AGGREGATION_PASSWORD,
                "",
            ).orEmpty(),
        )
    )
    val state: StateFlow<AggregationUiState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        MmkvManager.encodeSettings(AppConfig.PREF_AGGREGATION_ENABLED, enabled)
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_AGGREGATION_ENABLED)
        SettingsChangeManager.makeRestartService()
    }

    fun setHost(value: String) {
        val host = value.trim()
        _state.update { it.copy(host = host) }
        persist(AppConfig.PREF_AGGREGATION_HOST, host)
    }

    fun setPort(value: String) {
        val port = value.filter { it.isDigit() }.take(5)
        _state.update { it.copy(port = port) }
        persist(AppConfig.PREF_AGGREGATION_PORT, port)
    }

    fun setUsername(value: String) {
        val username = value.trim()
        _state.update { it.copy(username = username) }
        persist(AppConfig.PREF_AGGREGATION_USERNAME, username)
    }

    fun setPassword(value: String) {
        _state.update { it.copy(password = value) }
        persist(AppConfig.PREF_AGGREGATION_PASSWORD, value)
    }

    fun checkConnection() {
        val snapshot = _state.value
        val host = snapshot.host.trim()
        if (host.isEmpty()) {
            _state.update { it.copy(check = AggregationCheck.InvalidHost) }
            return
        }
        val port = parseAggregationPort(snapshot.port)
        if (port == null) {
            _state.update { it.copy(check = AggregationCheck.InvalidPort) }
            return
        }
        _state.update { it.copy(checking = true, check = null) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    }
                }.exceptionOrNull()
            }
            if (error != null) {
                LogUtil.w(
                    AppConfig.TAG,
                    "Aggregation balancer check failed for $host:$port",
                    error,
                )
            }
            _state.update {
                it.copy(
                    checking = false,
                    check = if (error == null) {
                        AggregationCheck.Success(host, port)
                    } else {
                        AggregationCheck.Failure(host, port)
                    },
                )
            }
        }
    }

    private fun persist(key: String, value: String) {
        MmkvManager.encodeSettings(key, value)
        SettingsChangeManager.notifySettingChanged(key)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
    }
}
