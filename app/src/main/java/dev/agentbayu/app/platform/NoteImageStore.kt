package dev.agentbayu.app.platform

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteImageStore(
    context: Context,
    private val clock: Clock = RealClock
) {

    private val pipeline = ImagePipeline(context.applicationContext)
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
    private val counter = AtomicLong(0L)

    suspend fun save(uri: Uri): File? = withContext(Dispatchers.IO) {
        val prepared = pipeline.prepare(uri) ?: return@withContext null
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return@withContext null
            val target = uniqueFile()
            target.writeBytes(prepared.bytes)
            target
        } catch (error: IOException) {
            Log.e(TAG, "Unable to store note image", error)
            null
        }
    }

    suspend fun prune(referencedText: String): Int = withContext(Dispatchers.IO) {
        pruneUnreferencedImages(directory, referencedText, clock.nowMillis())
    }

    private fun uniqueFile(): File {
        val stamp = clock.nowMillis().toString(RADIX)
        var candidate: File
        do {
            val suffix = counter.incrementAndGet().toString(RADIX)
            candidate = File(directory, FILE_PREFIX + stamp + "-" + suffix + FILE_SUFFIX)
        } while (candidate.exists())
        return candidate
    }

    companion object {
        const val DIRECTORY_NAME = "note-images"
        const val MIN_ORPHAN_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private const val FILE_PREFIX = "img-"
        private const val FILE_SUFFIX = ".jpg"
        private const val RADIX = 36
        private const val TAG = "NoteImageStore"
    }
}

internal fun pruneUnreferencedImages(
    directory: File,
    referencedText: String,
    nowMillis: Long,
    minAgeMillis: Long = NoteImageStore.MIN_ORPHAN_AGE_MILLIS
): Int {
    val files = directory.listFiles() ?: return 0
    var removed = 0
    files.forEach { file ->
        val orphan = file.isFile &&
            !referencedText.contains(file.name) &&
            nowMillis - file.lastModified() >= minAgeMillis
        if (orphan && file.delete()) removed += 1
    }
    return removed
}
