package com.localai.workspace.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.PowerManager

data class DeviceMeasurement(val pssBytes: Long?, val availableRamBytes: Long?, val totalRamBytes: Long?, val thermal: String?)

object DeviceMeasurements {
    fun capture(context: Context): DeviceMeasurement {
        val memory = runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        }.getOrNull()
        val thermal = runCatching { context.getSystemService(PowerManager::class.java).currentThermalStatus }.getOrNull()
        return DeviceMeasurement(runCatching { Debug.getPss().takeIf { it > 0 }?.times(1024) }.getOrNull(),
            memory?.availMem, memory?.totalMem, thermal?.let(::thermalName))
    }
    fun thermalName(status: Int): String = when (status) {
        0 -> "THERMAL_STATUS_NONE"; 1 -> "THERMAL_STATUS_LIGHT"; 2 -> "THERMAL_STATUS_MODERATE"
        3 -> "THERMAL_STATUS_SEVERE"; 4 -> "THERMAL_STATUS_CRITICAL"; 5 -> "THERMAL_STATUS_EMERGENCY"
        6 -> "THERMAL_STATUS_SHUTDOWN"; else -> "UNKNOWN"
    }
}
