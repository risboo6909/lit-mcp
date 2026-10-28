package com.github.risboo6909.utils

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class MirrorManagerTest {
    private val mirrors = listOf("https://primary.test", "https://backup-one.test", "https://backup-two.test")

    @Test
    fun implementsSharedMirrorAccessContract() {
        val access: MirrorAccess = MirrorManager(mirrors)

        assertEquals("https://primary.test", access.preferredMirrorOrigin())
        assertEquals(mirrors.toSet(), access.allowedMirrorOrigins())
        assertTrue(access.isAllowedMirrorUrl("https://backup-one.test/b/1"))
        assertFalse(access.isAllowedMirrorUrl("https://evil.test/b/1"))
    }

    @Test
    fun buildsAttemptOrderFromPreferredMirrorAndRetryBudget() {
        val manager = MirrorManager(mirrors)

        assertEquals(
            listOf(
                "https://primary.test",
                "https://backup-one.test",
                "https://backup-two.test",
                "https://primary.test",
            ),
            manager.mirrorAttempts(4),
        )
    }

    @Test
    fun successfulMirrorBecomesPreferredUntilPrimaryCooldown() {
        val time = AtomicLong(0)
        val manager = MirrorManager(mirrors) { time.get() }

        manager.recordSuccess("https://backup-one.test/b/1")
        assertEquals(
            listOf("https://backup-one.test", "https://primary.test", "https://backup-two.test"),
            manager.mirrorAttempts(3),
        )

        time.set(301_000_000_000L)
        assertEquals(
            listOf("https://primary.test", "https://backup-one.test", "https://backup-two.test"),
            manager.mirrorAttempts(3),
        )
    }

    @Test
    fun recognizesAndValidatesConfiguredMirrorUrlsOnly() {
        val manager = MirrorManager(
            listOf("https://backup-one.test"),
            sourceOrigins = setOf("https://primary.test"),
        )

        assertTrue(manager.handles("https://backup-one.test/b/1"))
        assertTrue(manager.handles("https://primary.test/b/1"))
        assertTrue(manager.isAllowedMirrorUrl("https://backup-one.test/b/1"))
        assertFalse(manager.isAllowedMirrorUrl("https://evil.test/b/1"))
        assertFalse(manager.isAllowedMirrorUrl("https://primary.test/b/1"))
    }

    @Test
    fun ownsRetryLoopAndRecordsSuccessfulMirror() = runBlocking {
        val manager = MirrorManager(mirrors)
        val hosts = mutableListOf<String>()

        val result = manager.fetchWithFailover("https://primary.test/b/1", 3) { candidate ->
            hosts += java.net.URI(candidate).host
            if (hosts.size == 1) {
                MirrorResponse(503, "unavailable", candidate)
            } else {
                MirrorResponse(200, "book", candidate)
            }
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf("primary.test", "backup-one.test"), hosts)
        assertEquals("book", result.getOrThrow().body)
        assertEquals("https://backup-one.test", manager.preferredMirrorOrigin())
    }
}
