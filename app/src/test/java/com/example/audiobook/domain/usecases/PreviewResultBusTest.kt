package com.example.audiobook.domain.usecases

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FIX 3 — قناة نتيجة المعاينة: نشر/مسح/أول-غير-null-يفوز. */
class PreviewResultBusTest {

    @Test
    fun clearEmptiesTheChannel() = runBlocking {
        PreviewResultBus.publishFailed("x")
        PreviewResultBus.clear()
        assertNull(PreviewResultBus.result.value)
    }

    @Test
    fun failedCarriesReason() = runBlocking {
        PreviewResultBus.clear()
        PreviewResultBus.publishFailed("scan-busy")
        val result = PreviewResultBus.result.value
        assertTrue(result is PreviewResultBus.PreviewResult.Failed)
        assertEquals("scan-busy", (result as PreviewResultBus.PreviewResult.Failed).reason)
        PreviewResultBus.clear()
    }

    @Test
    fun cancelledIsDistinctFromFailed() = runBlocking {
        PreviewResultBus.clear()
        PreviewResultBus.publishCancelled()
        assertTrue(PreviewResultBus.result.value is PreviewResultBus.PreviewResult.Cancelled)
        PreviewResultBus.clear()
    }
}
