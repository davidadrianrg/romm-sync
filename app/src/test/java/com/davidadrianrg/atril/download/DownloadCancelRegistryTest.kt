package com.davidadrianrg.atril.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCancelRegistryTest {

    // Cada test usa romIds distintos: el registro es un singleton de proceso.

    @Test
    fun singleCancelIsConsumedOnce() {
        DownloadCancelRegistry.markCancelled(9001)
        assertTrue(DownloadCancelRegistry.consumeUserCancel(9001, workStartedAtMs = Long.MAX_VALUE))
        assertFalse(DownloadCancelRegistry.consumeUserCancel(9001, workStartedAtMs = Long.MAX_VALUE))
    }

    @Test
    fun withoutMarkAStopKeepsThePartial() {
        assertFalse(DownloadCancelRegistry.consumeUserCancel(9002, workStartedAtMs = Long.MAX_VALUE))
    }

    @Test
    fun cancelAllAffectsWorkersStartedBeforeIt() {
        DownloadCancelRegistry.markAllCancelled(nowMs = 1_000L)
        assertTrue(DownloadCancelRegistry.consumeUserCancel(9003, workStartedAtMs = 500L))
        assertTrue(DownloadCancelRegistry.consumeUserCancel(9004, workStartedAtMs = 1_000L))
    }

    @Test
    fun cancelAllDoesNotAffectWorkersStartedAfterIt() {
        DownloadCancelRegistry.markAllCancelled(nowMs = 1_000L)
        assertFalse(DownloadCancelRegistry.consumeUserCancel(9005, workStartedAtMs = 2_000L))
    }

    @Test
    fun newWorkerClearsAStaleMark() {
        DownloadCancelRegistry.markCancelled(9006)
        DownloadCancelRegistry.clear(9006)
        assertFalse(DownloadCancelRegistry.consumeUserCancel(9006, workStartedAtMs = Long.MAX_VALUE))
    }
}
