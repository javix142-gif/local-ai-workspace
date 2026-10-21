package com.localai.workspace.tools

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the permission requested by the packaged application, after manifest merging. */
@RunWith(AndroidJUnit4::class)
class AlarmPermissionManifestTest {
    @Suppress("DEPRECATION")
    @Test
    fun packagedApplicationRequestsSetAlarmPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(
            "The merged application manifest must request $SET_ALARM_PERMISSION",
            SET_ALARM_PERMISSION in requestedPermissions,
        )
    }

    private companion object {
        const val SET_ALARM_PERMISSION = "com.android.alarm.permission.SET_ALARM"
    }
}
