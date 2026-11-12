package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EqualizerPresetTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun presetPublishesOneCompleteEnabledCurveWithoutIntermediateBands() = runBlocking {
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = temporary.newFolder("equalizer").resolve("settings.preferences_pb")
        val repository = EqualizerRepository(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file }))
        val initialized = CompletableDeferred<Unit>()
        val applied = CompletableDeferred<Unit>()
        val received = mutableListOf<EqualizerSettings>()
        val observer = launch {
            repository.settings.collect { received += it; initialized.complete(Unit); if (it.enabled) applied.complete(Unit) }
        }
        try {
            withTimeout(5_000) { initialized.await() }
            val curve = listOf(100, -200, 300, -400, 500, -600, 700)
            repository.setCurve(curve)
            withTimeout(5_000) { applied.await() }
            assertEquals(listOf(EqualizerSettings(false, emptyList()), EqualizerSettings(true, curve)), received)
            assertEquals(EqualizerSettings(true, curve), repository.settings.first())
        } finally { observer.cancelAndJoin(); storeScope.coroutineContext.job.cancelAndJoin() }
    }
}
