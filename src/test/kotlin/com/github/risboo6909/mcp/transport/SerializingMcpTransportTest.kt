package com.github.risboo6909.mcp.transport

import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpServerTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class SerializingMcpTransportTest {
    @Test
    fun concurrentSendsWaitForAsynchronousEnqueueCompletion() {
        val delegate = mock<McpServerTransport>()
        val active = AtomicInteger()
        val received = CopyOnWriteArrayList<Any>()
        val messages = (1..100).map { McpSchema.JSONRPCResponse("2.0", it, "ok", null) }
        messages.forEach { message ->
            whenever(delegate.sendMessage(message)).thenReturn(
                Mono.defer {
                    if (active.incrementAndGet() != 1) {
                        active.decrementAndGet()
                        Mono.error<Void>(IllegalStateException("Concurrent enqueue"))
                    } else {
                        Mono.delay(Duration.ofMillis(1)).doOnNext {
                            received += message.id()
                            active.decrementAndGet()
                        }.then()
                    }
                },
            )
        }
        val transport = SerializingMcpTransport(delegate)

        Flux.fromIterable(messages)
            .flatMap({ message -> transport.sendMessage(message).subscribeOn(Schedulers.parallel()) }, 16)
            .then().block(Duration.ofSeconds(10))

        assertEquals((1..100).toSet(), received.toSet())
        assertEquals(100, received.size)
        assertEquals(0, active.get())
    }

    @Test
    fun failedSendDoesNotPreventNextSend() {
        val delegate = mock<McpServerTransport>()
        val first = McpSchema.JSONRPCResponse("2.0", 1, "first", null)
        val second = McpSchema.JSONRPCResponse("2.0", 2, "second", null)
        val failure = IllegalStateException("closed")
        whenever(delegate.sendMessage(first)).thenReturn(Mono.error(failure))
        var delivered = false
        whenever(delegate.sendMessage(second)).thenReturn(Mono.fromRunnable { delivered = true })
        val transport = SerializingMcpTransport(delegate)

        val thrown = assertThrows(IllegalStateException::class.java) {
            transport.sendMessage(first).block(Duration.ofSeconds(5))
        }
        assertSame(failure, thrown)
        transport.sendMessage(second).block(Duration.ofSeconds(5))
        assertTrue(delivered)
    }
}
