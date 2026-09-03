package dev.agentbayu.app.platform

import android.content.Context
import java.io.File
import java.io.IOException

interface BinaryStorage {
    fun read(name: String): ByteArray?

    fun write(name: String, content: ByteArray)

    fun delete(name: String)

    fun names(): List<String>
}

class FileStorage(context: Context, directoryName: String) : BinaryStorage {

    private val directory = File(context.filesDir, directoryName)
    private val lock = Any()

    override fun read(name: String): ByteArray? = synchronized(lock) {
        val file = File(directory, name)
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (error: IOException) {
            null
        }
    }

    override fun write(name: String, content: ByteArray) {
        synchronized(lock) {
            if (!directory.exists()) directory.mkdirs()
            val temporary = File(directory, name + TEMP_SUFFIX)
            temporary.writeBytes(content)
            val target = File(directory, name)
            if (!temporary.renameTo(target)) {
                target.writeBytes(content)
                temporary.delete()
            }
        }
    }

    override fun delete(name: String) {
        synchronized(lock) {
            File(directory, name).delete()
            File(directory, name + TEMP_SUFFIX).delete()
        }
    }

    override fun names(): List<String> = synchronized(lock) {
        val entries = directory.listFiles() ?: return emptyList()
        return entries.filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }.map { it.name }
    }

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
    }
}

class InMemoryBinaryStorage : BinaryStorage {

    private val entries = LinkedHashMap<String, ByteArray>()

    override fun read(name: String): ByteArray? = synchronized(entries) { entries[name] }

    override fun write(name: String, content: ByteArray) {
        synchronized(entries) { entries[name] = content }
    }

    override fun delete(name: String) {
        synchronized(entries) { entries.remove(name) }
    }

    override fun names(): List<String> = synchronized(entries) { entries.keys.toList() }
}
