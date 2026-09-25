package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.InMemoryStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaTest {

    @Test
    fun `codex headers become both quota windows`() {
        val snapshot = QuotaParser.parse(
            mapOf(
                "X-Codex-5h-Usage" to "42.5",
                "x-codex-5h-limit" to "100",
                "x-codex-5h-reset-at" to "1800000000",
                "x-codex-7d-usage" to "10",
                "x-codex-7d-limit" to "50",
                "x-codex-7d-reset-at" to "2026-01-02T03:04:05Z"
            )
        )

        check(snapshot != null)
        assertEquals(2, snapshot.windows.size)
        val short = snapshot.windows.first { it.id == QuotaParser.WINDOW_5H }
        assertEquals(42.5, short.used!!, 0.001)
        assertEquals(100.0, short.limit!!, 0.001)
        assertEquals(42.5, short.percentUsed!!, 0.001)
        assertEquals(1_800_000_000_000L, short.resetAtMillis!!)
        assertEquals(20.0, snapshot.windows.first { it.id == QuotaParser.WINDOW_7D }.percentUsed!!, 0.001)
    }

    @Test
    fun `partial headers still report a window`() {
        val snapshot = QuotaParser.parse(mapOf("x-codex-5h-usage" to "7"))

        check(snapshot != null)
        assertEquals(1, snapshot.windows.size)
        assertEquals(7.0, snapshot.windows.single().percentUsed!!, 0.001)
    }

    @Test
    fun `headers without quota data produce no snapshot`() {
        assertNull(QuotaParser.parse(emptyMap()))
        assertNull(QuotaParser.parse(mapOf("content-type" to "text/event-stream")))
    }

    @Test
    fun `reset timestamps accept epoch seconds and iso text`() {
        assertEquals(1_700_000_000_000L, QuotaParser.resetAtMillis("1700000000"))
        assertEquals(1_700_000_000_000L, QuotaParser.resetAtMillis("1700000000000"))
        assertEquals(
            1_767_323_045_000L,
            QuotaParser.resetAtMillis("2026-01-02T03:04:05Z")
        )
        assertNull(QuotaParser.resetAtMillis(null))
        assertNull(QuotaParser.resetAtMillis("soon"))
    }

    @Test
    fun `snapshots persist per connection and survive a reload`() {
        val storage = InMemoryStorage()
        val clock = FakeClock()
        val store = QuotaStore(storage, clock)

        store.record("conn-1", mapOf("x-codex-5h-usage" to "3", "x-codex-5h-limit" to "30"))
        store.record("conn-2", mapOf("x-codex-5h-usage" to "9"))
        store.record("conn-3", mapOf("content-type" to "text/event-stream"))

        assertEquals(3.0, store.snapshotFor("conn-1")?.windows?.single()?.used!!, 0.001)
        assertEquals(9.0, store.snapshotFor("conn-2")?.windows?.single()?.used!!, 0.001)
        assertNull(store.snapshotFor("conn-3"))

        val reloaded = QuotaStore(storage, clock)
        assertEquals(3.0, reloaded.snapshotFor("conn-1")?.windows?.single()?.used!!, 0.001)

        reloaded.forget("conn-1")
        assertNull(reloaded.snapshotFor("conn-1"))
        assertNull(QuotaStore(storage, clock).snapshotFor("conn-1"))
    }
}
