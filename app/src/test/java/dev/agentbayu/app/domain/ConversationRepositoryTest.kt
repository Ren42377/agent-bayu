package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryTest {

    @Test
    fun emptyByDefault() {
        val repository = ConversationRepository()
        assertTrue(repository.messages.value.isEmpty())
    }

    @Test
    fun appendKeepsOrderAndAssignsIncreasingIds() {
        val repository = ConversationRepository()
        repository.append(MessageAuthor.USER, "first")
        repository.append(MessageAuthor.AGENT, "second")

        val messages = repository.messages.value
        assertEquals(2, messages.size)
        assertEquals(MessageAuthor.USER, messages[0].author)
        assertEquals("first", messages[0].text)
        assertEquals(MessageAuthor.AGENT, messages[1].author)
        assertTrue(messages[1].id > messages[0].id)
    }

    @Test
    fun idsDoNotCollideAfterClear() = runTest {
        val repository = ConversationRepository()
        repository.append(MessageAuthor.USER, "old")
        repository.clear()
        repository.append(MessageAuthor.USER, "new")

        val messages = repository.messages.value
        assertEquals(1, messages.size)
        assertEquals("new", messages.single().text)
    }

    @Test
    fun deltasAccumulateOnTheTargetMessage() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)

        repository.appendDelta(placeholder.id, "Hal")
        repository.appendDelta(placeholder.id, "")
        repository.appendDelta(placeholder.id, "lo")

        val message = repository.messages.value.single()
        assertEquals("Hallo", message.text)
        assertTrue(message.streaming)
    }

    @Test
    fun unknownIdsAreIgnored() {
        val repository = ConversationRepository()
        val message = repository.append(MessageAuthor.USER, "satu")

        repository.appendDelta(message.id + 99L, "hantu")
        repository.replaceText(message.id + 99L, "hantu")
        repository.complete(message.id + 99L, detail(), TokenUsage(1, 1))

        assertEquals(listOf("satu"), repository.messages.value.map { it.text })
    }

    @Test
    fun completeAttachesTheDetailAndClosesTheStream() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        repository.appendDelta(placeholder.id, "jawaban")

        repository.complete(placeholder.id, detail(), TokenUsage(12, 8, 0.001, false))

        val message = repository.messages.value.single()
        assertEquals("jawaban", message.text)
        assertEquals("kilocode", message.detail?.providerId)
        assertEquals(20, message.usage?.totalTokens)
        assertFalse(message.streaming)
    }

    @Test
    fun completeKeepsAnEarlierDetailWhenNoneIsGiven() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        repository.attachDetail(placeholder.id, detail())

        repository.complete(placeholder.id, null, null)

        val message = repository.messages.value.single()
        assertEquals("kilocode", message.detail?.providerId)
        assertNull(message.usage)
        assertFalse(message.streaming)
    }

    @Test
    fun finishStreamingOnlyTouchesStreamingMessages() {
        val repository = ConversationRepository()
        val done = repository.append(MessageAuthor.USER, "selesai")
        val streaming = repository.append(MessageAuthor.AGENT, "sedang", streaming = true)

        repository.finishStreaming(done.id)
        repository.finishStreaming(streaming.id)

        assertEquals(listOf(false, false), repository.messages.value.map { it.streaming })
        assertEquals(done, repository.messages.value.first())
    }

    @Test
    fun restoreClosesOpenStreamsAndContinuesTheIdSequence() {
        val repository = ConversationRepository()
        repository.restore(
            listOf(
                ChatMessage(id = 7L, author = MessageAuthor.USER, text = "lama"),
                ChatMessage(id = 8L, author = MessageAuthor.AGENT, text = "setengah", streaming = true)
            )
        )

        val appended = repository.append(MessageAuthor.USER, "baru")

        assertEquals(9L, appended.id)
        assertEquals(listOf("lama", "setengah", "baru"), repository.messages.value.map { it.text })
        assertEquals(listOf(false, false, false), repository.messages.value.map { it.streaming })
    }

    @Test
    fun restoreClearsTheCurrentConversationForAnEmptyHistory() {
        val repository = ConversationRepository()
        repository.append(MessageAuthor.USER, "ada")

        repository.restore(emptyList())

        assertTrue(repository.messages.value.isEmpty())
    }

    @Test
    fun restartFromReplacesTheBranchInOneStateUpdate() {
        val repository = ConversationRepository()
        val first = repository.append(MessageAuthor.USER, "first")
        repository.append(MessageAuthor.AGENT, "first reply")
        val edited = repository.append(MessageAuthor.USER, "old")
        repository.append(MessageAuthor.AGENT, "old reply")

        val turn = repository.restartFrom(edited.id, " replacement ", emptyList())

        assertEquals(listOf(first.id, 2L), turn?.history?.map { it.id })
        assertEquals("replacement", turn?.prompt?.text)
        assertTrue(turn?.placeholder?.streaming == true)
        assertEquals(
            listOf("first", "first reply", "replacement", ""),
            repository.messages.value.map { it.text }
        )
    }

    @Test
    fun restartFromRejectsAnAgentMessageWithoutChangingState() {
        val repository = ConversationRepository()
        val message = repository.append(MessageAuthor.AGENT, "reply")
        val before = repository.messages.value

        val turn = repository.restartFrom(message.id, "replacement", emptyList())

        assertNull(turn)
        assertEquals(before, repository.messages.value)
    }

    @Test
    fun regenerateFromRejectsUnrelatedMessagesWithoutChangingState() {
        val repository = ConversationRepository()
        val prompt = repository.append(MessageAuthor.USER, "prompt")
        val secondPrompt = repository.append(MessageAuthor.USER, "second")
        val before = repository.messages.value

        val turn = repository.regenerateFrom(prompt.id, secondPrompt.id)

        assertNull(turn)
        assertEquals(before, repository.messages.value)
    }

    @Test
    fun attachmentIdsFromReturnsOnlyTheSelectedBranch() {
        val repository = ConversationRepository()
        val earlier = MessageAttachment(id = "earlier", mimeType = "image/jpeg")
        val selected = MessageAttachment(id = "selected", mimeType = "image/jpeg")
        val later = MessageAttachment(id = "later", mimeType = "image/jpeg")
        repository.append(MessageAuthor.USER, "earlier", attachments = listOf(earlier))
        val target = repository.append(
            MessageAuthor.USER,
            "target",
            attachments = listOf(selected)
        )
        repository.append(MessageAuthor.AGENT, "reply")
        repository.append(MessageAuthor.USER, "later", attachments = listOf(later))

        assertEquals(setOf("selected", "later"), repository.attachmentIdsFrom(target.id))
    }

    @Test
    fun deltasCollapseIntoOneProseSegment() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)

        repository.appendDelta(placeholder.id, "Hal")
        repository.appendDelta(placeholder.id, "lo")

        val segments = repository.messages.value.single().segments
        assertEquals(listOf(MessageSegment.Prose("Hallo")), segments)
    }

    @Test
    fun segmentsKeepTheOrderTheEventsArrivedIn() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)

        repository.appendThinking(placeholder.id, "menimbang")
        repository.closeThinking(placeholder.id, 3_200L)
        repository.appendDelta(placeholder.id, "Saya cek dulu.")
        repository.startToolRun(placeholder.id, "read_file", "read_file {\"path\":\"a.txt\"}")
        repository.finishToolRun(placeholder.id, "read_file", true)
        repository.appendDelta(placeholder.id, " Sudah.")

        val message = repository.messages.value.single()
        assertEquals(
            listOf(
                MessageSegment.Thinking(text = "menimbang", millis = 3_200L, done = true),
                MessageSegment.Prose("Saya cek dulu."),
                MessageSegment.Tool(
                    name = "read_file",
                    label = "read_file {\"path\":\"a.txt\"}",
                    running = false,
                    ok = true
                ),
                MessageSegment.Prose(" Sudah.")
            ),
            message.segments
        )
        assertEquals("Saya cek dulu. Sudah.", message.text)
    }

    @Test
    fun thinkingAfterAToolOpensASecondSegment() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)

        repository.appendThinking(placeholder.id, "satu")
        repository.closeThinking(placeholder.id, 1_000L)
        repository.appendThinking(placeholder.id, "dua")

        val segments = repository.messages.value.single().segments
        assertEquals(2, segments.size)
        assertEquals("satu", (segments.first() as MessageSegment.Thinking).text)
        assertEquals("dua", (segments.last() as MessageSegment.Thinking).text)
        assertFalse((segments.last() as MessageSegment.Thinking).done)
    }

    @Test
    fun finishStreamingSettlesWhateverWasStillOpen() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        repository.appendThinking(placeholder.id, "berhenti di tengah")
        repository.startToolRun(placeholder.id, "search_files", "")

        repository.finishStreaming(placeholder.id)

        val segments = repository.messages.value.single().segments
        assertTrue((segments.first() as MessageSegment.Thinking).done)
        val tool = segments.last() as MessageSegment.Tool
        assertFalse(tool.running)
        assertFalse(tool.ok)
    }

    @Test
    fun aStoredMessageWithoutSegmentsStillRenders() {
        val message = ChatMessage(id = 1L, author = MessageAuthor.AGENT, text = "jawaban lama")

        assertEquals(listOf(MessageSegment.Prose("jawaban lama")), message.displaySegments)
    }

    private fun detail(): ReplyDetail = ReplyDetail(
        providerId = "kilocode",
        providerLabel = "Kilo Code",
        model = "minimax/minimax-m3:free",
        connectionId = "conn-1",
        connectionLabel = "Kilo Code",
        authKind = AuthKind.NONE
    )
}
