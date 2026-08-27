package dev.agentbayu.app.ai.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseReaderTest {

    private val reader = SseReader()

    private fun data(vararg lines: String): List<String> {
        val payloads = ArrayList<String>()
        lines.forEach { line ->
            reader.accept(line).forEach { signal ->
                if (signal is SseSignal.Data) payloads += signal.json
            }
        }
        reader.flush().forEach { signal ->
            if (signal is SseSignal.Data) payloads += signal.json
        }
        return payloads
    }

    @Test
    fun blankLineEmitsTheBufferedEvent() {
        assertTrue(reader.accept("data: {\"a\":1}").isEmpty())
        assertEquals(listOf(SseSignal.Data("{\"a\":1}")), reader.accept(""))
    }

    @Test
    fun theSpaceAfterTheFieldNameIsOptional() {
        assertEquals(listOf("{\"a\":1}"), data("data:{\"a\":1}", ""))
    }

    @Test
    fun carriageReturnsAreStripped() {
        assertTrue(reader.accept("data: {\"a\":1}\r").isEmpty())
        assertEquals(listOf(SseSignal.Data("{\"a\":1}")), reader.accept("\r"))
    }

    @Test
    fun commentsAreIgnored() {
        assertTrue(reader.accept(": keep-alive").isEmpty())
        assertTrue(reader.accept(":").isEmpty())
        assertEquals(emptyList<String>(), data(": ping", ""))
    }

    @Test
    fun otherFieldsAreIgnored() {
        assertEquals(emptyList<String>(), data("event: message", "id: 42", "retry: 1000", "garbage", ""))
    }

    @Test
    fun blankLinesWithoutDataEmitNothing() {
        assertTrue(reader.accept("").isEmpty())
        assertTrue(reader.accept("").isEmpty())
    }

    @Test
    fun doneSentinelFlushesThenSignalsCompletion() {
        assertTrue(reader.accept("data: {\"a\":1}").isEmpty())
        assertEquals(
            listOf(SseSignal.Data("{\"a\":1}"), SseSignal.Done),
            reader.accept("data: [DONE]")
        )
    }

    @Test
    fun doneSentinelWorksWithoutPendingDataOrSpacing() {
        assertEquals(listOf(SseSignal.Done), reader.accept("data:[DONE]"))
    }

    @Test
    fun consecutiveCompleteEventsAreSplit() {
        assertEquals(
            listOf("{\"i\":1}", "{\"i\":2}", "{\"i\":3}"),
            data("data: {\"i\":1}", "data: {\"i\":2}", "data: {\"i\":3}", "")
        )
    }

    @Test
    fun eventsSurvivingBlankLinesStayIndependent() {
        assertEquals(
            listOf("{\"i\":1}", "{\"i\":2}"),
            data("data: {\"i\":1}", "", "data: {\"i\":2}", "")
        )
    }

    @Test
    fun splitPayloadsAreJoined() {
        assertEquals(listOf("{\"a\":\n1}"), data("data: {\"a\":", "data: 1}", ""))
    }

    @Test
    fun brokenJsonIsPassedThroughUntouched() {
        assertEquals(listOf("not json at all"), data("data: not json at all", ""))
    }

    @Test
    fun whitespaceOnlyPayloadsAreDropped() {
        assertEquals(emptyList<String>(), data("data:   ", ""))
    }

    @Test
    fun flushIsIdempotent() {
        reader.accept("data: {\"a\":1}")
        assertEquals(listOf(SseSignal.Data("{\"a\":1}")), reader.flush())
        assertTrue(reader.flush().isEmpty())
    }

    @Test
    fun arrayPayloadsCountAsComplete() {
        assertEquals(listOf("[1]", "[2]"), data("data: [1]", "data: [2]", ""))
    }
}
