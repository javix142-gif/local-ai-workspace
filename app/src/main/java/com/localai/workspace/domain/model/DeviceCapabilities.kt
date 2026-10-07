package com.localai.workspace.domain.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

data class DeviceCapabilities(
    val androidVersion: Int,
    val abi: String,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val cpuCores: Int,
    val gpuDescription: String?,
    val knownAccelerators: Set<AcceleratorType>,
    val storageAvailableBytes: Long,
)

/** Device facts used for warnings only; an accelerator is not claimed without runtime support. */
class DeviceCapabilitiesProvider(private val context: Context) {
    fun read(): DeviceCapabilities {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val storage = StatFs(context.filesDir.path)
        return DeviceCapabilities(
            androidVersion = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            ramTotalBytes = memoryInfo.totalMem,
            ramAvailableBytes = memoryInfo.availMem,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            gpuDescription = null,
            knownAccelerators = setOf(AcceleratorType.CPU),
            storageAvailableBytes = storage.availableBytes,
        )
    }
}
