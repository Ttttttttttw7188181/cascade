package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Live traffic sample pushed from the daemon process to the main screen.
 *
 * Speeds are bytes per second; totals are bytes for the current session.
 */
data class TrafficMessage(
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
    val totalUp: Long = 0,
    val totalDown: Long = 0,
    val elapsedSeconds: Long = 0,
) : Serializable
