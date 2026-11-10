package com.narezany.cascade.engine

import com.narezany.cascade.data.EngineState
import com.narezany.cascade.data.EngineStatus
import com.narezany.cascade.data.TrafficPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process state shared by the VPN service and the Compose UI. The service and the
 * activity run in the same process on purpose so traffic counters never need a binder.
 */
object CascadeRuntime {
    private val _engine = MutableStateFlow(EngineState())
    val engine: StateFlow<EngineState> = _engine.asStateFlow()

    private val _traffic = MutableStateFlow<List<TrafficPoint>>(emptyList())
    val traffic: StateFlow<List<TrafficPoint>> = _traffic.asStateFlow()

    @Synchronized
    fun starting(profileId: String, profileName: String) {
        _engine.value = EngineState(
            status = EngineStatus.STARTING,
            profileId = profileId,
            profileName = profileName,
        )
        _traffic.value = emptyList()
    }

    @Synchronized
    fun connected(profileId: String, profileName: String, pingMs: Long) {
        val current = _engine.value
        _engine.value = current.copy(
            status = EngineStatus.CONNECTED,
            profileId = profileId,
            profileName = profileName,
            startedAt = System.currentTimeMillis(),
            pingMs = pingMs,
            error = "",
        )
    }

    /** Keeps the session timer and counters after an in-place core reload. */
    @Synchronized
    fun refreshed(profileId: String, profileName: String, pingMs: Long) {
        _engine.update {
            it.copy(
                status = EngineStatus.CONNECTED,
                profileId = profileId,
                profileName = profileName,
                pingMs = pingMs,
                error = "",
            )
        }
    }

    @Synchronized
    fun updateTraffic(up: Long, down: Long, upSpeed: Long, downSpeed: Long) {
        _engine.update {
            it.copy(
                up = it.up + up,
                down = it.down + down,
                upSpeed = upSpeed,
                downSpeed = downSpeed,
            )
        }
        _traffic.update { points ->
            (points + TrafficPoint(up = upSpeed, down = downSpeed)).takeLast(90)
        }
    }

    @Synchronized
    fun ping(ms: Long) {
        _engine.update { it.copy(pingMs = ms) }
    }

    @Synchronized
    fun stopping() {
        _engine.update { it.copy(status = EngineStatus.STOPPING) }
    }

    @Synchronized
    fun stopped() {
        val current = _engine.value
        _engine.value = EngineState(status = EngineStatus.STOPPED, error = "")
        _traffic.value = emptyList()
        current.profileId
    }

    @Synchronized
    fun failed(message: String) {
        _engine.update {
            it.copy(
                status = EngineStatus.ERROR,
                error = message,
                upSpeed = 0,
                downSpeed = 0,
            )
        }
    }

    fun snapshot(): EngineState = _engine.value
}
