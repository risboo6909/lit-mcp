package com.github.risboo6909.utils

import com.github.risboo6909.mcp.McpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecuteWithTimeoutTest {
    @Test
    fun operationTimeoutReturnsErrorAndAnotherCallStillSucceeds() {
        val timedOut = executeWithTimeout<String>(50) { awaitCancellation() }
        val next = executeWithTimeout(1_000) { McpResponse(payload = "ok") }

        assertEquals(listOf("Error: timeout after 50ms"), timedOut.errors)
        assertEquals("ok", next.payload)
    }

    @Test
    fun cancellationFromOperationIsCurrentlyReturnedAsAnError() {
        val result = executeWithTimeout<String>(1_000) { throw CancellationException("cancelled") }

        assertEquals(listOf("Error: cancelled"), result.errors)
    }
}
