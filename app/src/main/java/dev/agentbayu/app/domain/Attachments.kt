package dev.agentbayu.app.domain

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.platform.BinaryStorage
import dev.agentbayu.app.platform.ImagePipeline
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Attachments(
    private val pipeline: ImagePipeline,
    private val storage: BinaryStorage,
    private val clock: Clock = RealClock
) {

    private val counter = AtomicLong(0L)
    private val pending = HashSet<String>()

    suspend fun accept(uri: Uri): MessageAttachment? = withContext(Dispatchers.IO) {
        val prepared = pipeline.prepare(uri) ?: return@withContext null
        val id = newId()
        storage.write(id, prepared.bytes)
        synchronized(pending) { pending += id }
        MessageAttachment(
            id = id,
            mimeType = prepared.mimeType,
            fileName = pipeline.fileNameOf(uri),
            width = prepared.width,
            height = prepared.height
        )
    }

    fun discard(id: String) {
        synchronized(pending) { pending -= id }
        storage.delete(id)
    }

    fun keep(ids: Set<String>) {
        val held = synchronized(pending) {
            pending.removeAll(ids)
            pending.toSet()
        }
        storage.names().forEach { name ->
            if (name !in ids && name !in held) storage.delete(name)
        }
    }

    fun image(attachment: MessageAttachment): ChatImage? {
        val bytes = storage.read(attachment.id) ?: return null
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return ChatImage(mimeType = attachment.mimeType, data = encoded)
    }

    suspend fun thumbnail(id: String, edge: Int = ImagePipeline.THUMBNAIL_EDGE): Bitmap? =
        withContext(Dispatchers.IO) {
            val bytes = storage.read(id) ?: return@withContext null
            pipeline.decodeScaled(bytes, edge)
        }

    private fun newId(): String =
        clock.nowMillis().toString(RADIX) + "-" + counter.incrementAndGet().toString(RADIX)

    private companion object {
        const val RADIX = 36
    }
}
