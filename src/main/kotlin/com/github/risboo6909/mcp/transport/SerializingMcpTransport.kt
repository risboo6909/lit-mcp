package com.github.risboo6909.mcp.transport

import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpServerTransport
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

internal class SerializingMcpTransport(private val delegate: McpServerTransport) : McpServerTransport by delegate {
    private val sendLock = Any()

    // SDK 0.17's stdio sink rejects concurrent producers (upstream issue #304).
    // Hold the lock until enqueue completes, including asynchronous readiness checks.
    // Run blocking coordination on boundedElastic, never on a Reactor event loop.
    override fun sendMessage(message: McpSchema.JSONRPCMessage): Mono<Void> = Mono.fromRunnable<Void> {
        synchronized(sendLock) {
            delegate.sendMessage(message).block()
        }
    }.subscribeOn(Schedulers.boundedElastic())
}
