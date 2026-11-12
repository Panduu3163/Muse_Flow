package com.example

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadServiceTimeoutTest {
    private lateinit var db: MuseFlowDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var repository: DownloadRepository
    private var previous: DownloadRepository? = null
    private val track = Track("Timeout", "Artist", "", "3:00", "", 0, sourceId = "timeout0001", sourceType = MusicSource.YOUTUBE_MUSIC)
    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val started = CompletableDeferred<Unit>()
        repository = DownloadRepository(context, db, object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                started.complete(Unit); awaitCancellation()
            }
        }, resolver = { _, _ -> StreamResolution("https://audio.test") }, startService = {}, online = { true }, repositoryScope = scope)
        previous = ReflectionHelpers.getStaticField(DownloadRepository::class.java, "instance")
        ReflectionHelpers.setStaticField(DownloadRepository::class.java, "instance", repository)
        repository.startDownload(track)
        withTimeout(5_000) { started.await() }
    }
    @After fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        ReflectionHelpers.setStaticField(DownloadRepository::class.java, "instance", previous)
        db.close()
    }

    @Test fun timeoutStopsForegroundServiceReleasesWakeLockAndPreservesRetryableIntent() = runBlocking {
        val controller = Robolectric.buildService(DownloadService::class.java).create()
        val service = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val wake = ShadowPowerManager.getLatestWakeLock()
        assertNotNull(wake); assertTrue(wake.isHeld)
        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertFalse(wake.isHeld)
        val rows = withTimeout(5_000) { repository.requests.first { it.any { row -> row.requestState == DownloadRequestState.INTERRUPTED } } }
        assertTrue(rows.single().error!!.contains("time limit"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(service).isForegroundStopped)
        controller.destroy()
        Unit
    }

    @Test fun foregroundStartRejectionStopsTheServiceWithoutCrashingOrLosingTheRequest() = runBlocking {
        val controller = Robolectric.buildService(DownloadService::class.java)
        shadowOf(controller.get()).setThrowInStartForeground(SecurityException("injected foreground denial"))
        controller.create()
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        val rows = withTimeout(5_000) { repository.requests.first { it.any { row -> row.requestState == DownloadRequestState.INTERRUPTED } } }
        assertTrue(rows.single().error!!.contains("could not keep downloads running"))
        controller.destroy()
        Unit
    }
}
