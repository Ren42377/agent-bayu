package dev.agentbayu.app.platform.files

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException

class FileAccessException(message: String) : Exception(message)

class FileAccess(roots: List<File>, blocked: List<File> = emptyList()) {

    private val roots: List<File> = roots.map { it.canonicalOrAbsolute() }

    private val blocked: List<File> = blocked.map { it.canonicalOrAbsolute() }

    val rootPaths: List<String> = this.roots.map { it.path }

    fun resolve(path: String): File {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) throw FileAccessException("The path is empty")
        val home = roots.firstOrNull() ?: throw FileAccessException("No storage root is available")
        val raw = if (trimmed.startsWith("/")) File(trimmed) else File(home, trimmed)
        val canonical = try {
            raw.canonicalFile
        } catch (error: IOException) {
            throw FileAccessException("Cannot resolve " + trimmed)
        }
        if (roots.none { root -> canonical.isInside(root) }) {
            throw FileAccessException("Outside the allowed storage: " + canonical.path)
        }
        if (canonical.isBlocked()) {
            throw FileAccessException("Off limits: " + canonical.path)
        }
        return canonical
    }

    fun list(path: String, limit: Int = MAX_ENTRIES): String {
        val target = resolve(path)
        if (!target.exists()) throw FileAccessException("Nothing at " + target.path)
        if (!target.isDirectory) return describe(target)
        val entries = target.listFiles() ?: throw FileAccessException("Cannot open " + target.path)
        if (entries.isEmpty()) return "Empty directory: " + target.path
        val sorted = entries
            .filterNot { it.isBlocked() }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        if (sorted.isEmpty()) return "Empty directory: " + target.path
        val head = sorted.take(limit).joinToString("\n") { describe(it) }
        val extra = sorted.size - limit
        return if (extra > 0) head + "\n" + extra + " more entries" else head
    }

    fun read(path: String, maxBytes: Int = MAX_READ_BYTES): String {
        val target = resolve(path)
        if (!target.exists()) throw FileAccessException("Nothing at " + target.path)
        if (target.isDirectory) throw FileAccessException("That is a directory: " + target.path)
        val length = target.length()
        if (length > maxBytes) {
            throw FileAccessException(
                "The file is " + length + " bytes, over the " + maxBytes + " byte limit"
            )
        }
        val bytes = try {
            target.readBytes()
        } catch (error: IOException) {
            throw FileAccessException("Cannot read " + target.path)
        } catch (error: OutOfMemoryError) {
            throw FileAccessException("The file does not fit in memory: " + target.path)
        }
        if (looksBinary(bytes)) throw FileAccessException("That file is binary: " + target.path)
        return String(bytes, Charsets.UTF_8)
    }

    fun textOrEmpty(path: String): String = try {
        read(path)
    } catch (error: FileAccessException) {
        ""
    }

    fun exists(path: String): Boolean = resolve(path).exists()

    fun write(path: String, content: String) {
        val target = resolve(path)
        if (target.isDirectory) throw FileAccessException("That is a directory: " + target.path)
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw FileAccessException("Cannot create " + parent.path)
        }
        try {
            target.writeText(content)
        } catch (error: IOException) {
            throw FileAccessException("Cannot write " + target.path)
        }
    }

    fun delete(path: String) {
        val target = resolve(path)
        if (!target.exists()) throw FileAccessException("Nothing at " + target.path)
        if (target.isDirectory && target.listFiles()?.isNotEmpty() == true) {
            throw FileAccessException("The directory is not empty: " + target.path)
        }
        if (!target.delete()) throw FileAccessException("Cannot delete " + target.path)
    }

    fun move(from: String, to: String) {
        val source = resolve(from)
        val destination = resolve(to)
        if (!source.exists()) throw FileAccessException("Nothing at " + source.path)
        if (destination.exists()) throw FileAccessException("Already there: " + destination.path)
        val parent = destination.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw FileAccessException("Cannot create " + parent.path)
        }
        if (source.renameTo(destination)) return
        try {
            source.copyTo(destination)
            if (!source.delete()) throw FileAccessException("Cannot remove " + source.path)
        } catch (error: IOException) {
            throw FileAccessException("Cannot move " + source.path)
        }
    }

    fun search(
        path: String,
        query: String,
        extension: String? = null,
        limit: Int = MAX_MATCHES
    ): String {
        if (query.isEmpty()) throw FileAccessException("The query is empty")
        val root = resolve(path)
        if (!root.exists()) throw FileAccessException("Nothing at " + root.path)
        val suffix = extension?.trim()?.removePrefix("*")?.removePrefix(".")?.lowercase()
        val matches = ArrayList<String>()
        val walk = root.walkTopDown()
            .maxDepth(MAX_SEARCH_DEPTH)
            .onEnter { directory -> !directory.name.startsWith(".") && !directory.isBlocked() }
        for (file in walk) {
            if (matches.size >= limit) break
            if (!file.isFile || file.length() > MAX_READ_BYTES) continue
            if (suffix != null && !file.name.lowercase().endsWith("." + suffix)) continue
            val bytes = try {
                file.readBytes()
            } catch (error: IOException) {
                continue
            } catch (error: OutOfMemoryError) {
                continue
            }
            if (looksBinary(bytes)) continue
            String(bytes, Charsets.UTF_8).lineSequence().forEachIndexed { index, line ->
                if (matches.size < limit && line.contains(query, ignoreCase = true)) {
                    matches += file.path + ":" + (index + 1) + ": " + line.trim().take(SNIPPET)
                }
            }
        }
        if (matches.isEmpty()) return "No match for " + query
        return matches.joinToString("\n")
    }

    private fun describe(file: File): String = if (file.isDirectory) {
        "dir  " + file.name
    } else {
        "file " + file.name + "  " + file.length() + " bytes"
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val probe = minOf(bytes.size, BINARY_PROBE_BYTES)
        if (probe == 0) return false
        var suspicious = 0
        for (index in 0 until probe) {
            val value = bytes[index].toInt()
            if (value == 0) return true
            if (value in 1..8 || value in 14..31) suspicious += 1
        }
        return suspicious * 100 / probe > BINARY_TOLERANCE
    }

    private fun File.isInside(root: File): Boolean =
        path == root.path || path.startsWith(root.path + File.separator)

    private fun File.isBlocked(): Boolean = blocked.any { limit -> isInside(limit) }

    companion object {
        const val MAX_READ_BYTES = 256 * 1024
        const val MAX_ENTRIES = 200
        const val MAX_MATCHES = 80
        const val MAX_SEARCH_DEPTH = 6
        const val BINARY_PROBE_BYTES = 4_096
        const val BINARY_TOLERANCE = 5
        const val SNIPPET = 200

        fun of(context: Context): FileAccess = FileAccess(
            roots = listOf(Environment.getExternalStorageDirectory()),
            blocked = privateDirectoriesOf(context)
        )

        private fun privateDirectoriesOf(context: Context): List<File> = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.noBackupFilesDir,
            context.dataDir
        )
    }
}

private fun File.canonicalOrAbsolute(): File = try {
    canonicalFile
} catch (error: IOException) {
    absoluteFile
}
