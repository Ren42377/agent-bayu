package dev.agentbayu.app.platform

import android.content.Context
import java.io.File
import java.io.IOException

class AppNoteStorage(context: Context) : TaskStorage {

    private val fallbackDirectory = File(context.filesDir, DIRECTORY_NAME)
    private val delegate: TaskStorage

    init {
        val externalDirectory = context.getExternalFilesDir(null)?.let { File(it, DIRECTORY_NAME) }
        val external = externalDirectory
            ?.takeIf { prepareDirectory(it) }
            ?.let(::DirectoryTaskStorage)
        val fallback = DirectoryTaskStorage(fallbackDirectory)
        delegate = when {
            external == null -> {
                if (!prepareDirectory(fallbackDirectory)) {
                    throw IOException("Cannot create note storage")
                }
                fallback
            }
            external.names().isNotEmpty() -> external
            else -> {
                migrateFallback(fallback, external)
                external
            }
        }
    }

    override fun read(name: String): String? = delegate.read(name)

    override fun write(name: String, content: String) = delegate.write(name, content)

    override fun delete(name: String) = delegate.delete(name)

    override fun names(): List<String> = delegate.names()

    override fun commit(writes: Map<String, String>, deletes: Set<String>) =
        delegate.commit(writes, deletes)

    private fun migrateFallback(source: DirectoryTaskStorage, target: TaskStorage) {
        val names = source.names()
        if (names.isEmpty()) return
        names.forEach { name ->
            val content = source.read(name) ?: throw IOException("Cannot read fallback note data")
            target.write(name, content)
        }
        source.clear()
        fallbackDirectory.delete()
    }

    private fun prepareDirectory(directory: File): Boolean =
        (directory.isDirectory || directory.mkdirs()) && directory.canWrite()

    companion object {
        const val DIRECTORY_NAME = "notes"
    }
}
