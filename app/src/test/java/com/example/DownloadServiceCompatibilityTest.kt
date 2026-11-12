package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DownloadServiceCompatibilityTest {
    private fun type(): Int = DownloadService::class.java.getDeclaredMethod("foregroundServiceType")
        .apply { isAccessible = true }.invoke(DownloadService()) as Int

    @Test @Config(sdk = [24]) fun oldAndroidUsesCompatibleForegroundType() { assertEquals(0, type()) }
    @Test @Config(sdk = [36]) fun modernAndroidRetainsDataSyncType() {
        assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, type())
    }
}
