package dev.agentbayu.app.platform

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

interface TaskStorage {
    fun read(name: String): String?

    fun write(name: String, content: String)

    fun delete(name: String)

    fun names(): List<String>

    fun commit(writes: Map<String, String>, deletes: Set<String>) {
        writes.filterKeys { it != AppTaskStorage.METADATA_FILE }.forEach { (name, content) ->
            write(name, content)
        }
        writes[AppTaskStorage.METADATA_FILE]?.let { metadata ->
            write(AppTaskStorage.METADATA_FILE, metadata)
        }
        deletes.filterNot(writes::containsKey).forEach(::delete)
    }

    fun clear() {
        names().forEach(::delete)
    }
}

class AppTaskStorage internal constructor(
    externalDirectory: File?,
    private val fallbackDirectory: File
) : TaskStorage {

    constructor(context: Context) : this(
        externalDirectory = context.getExternalFilesDir(null)?.let { File(it, DIRECTORY_NAME) },
        fallbackDirectory = File(context.filesDir, DIRECTORY_NAME)
    )

    private val external = externalDirectory?.let(::DirectoryTaskStorage)
    private val fallback = DirectoryTaskStorage(fallbackDirectory)
    private val delegate: DirectoryTaskStorage
    private var clearMarkerWritten = false

    init {
        applyPendingClear()
        val externalAvailable = external?.directory?.let(::prepareDirectory) == true
        val availableExternal = external?.takeIf { externalAvailable }
        delegate = when {
            availableExternal == null -> {
                if (!prepareDirectory(fallbackDirectory)) {
                    throw IOException("Cannot create task storage")
                }
                fallback
            }
            migrateFallback(availableExternal) -> availableExternal
            else -> fallback
        }
    }

    override fun read(name: String): String? = delegate.read(name)

    override fun commit(writes: Map<String, String>, deletes: Set<String>) {
        ensureWritable()
        delegate.commit(writes, deletes)
    }

    override fun write(name: String, content: String) {
        ensureWritable()
        delegate.write(name, content)
    }

    override fun delete(name: String) {
        ensureWritable()
        delegate.delete(name)
    }

    override fun names(): List<String> = delegate.names()

    override fun clear() {
        writeClearMarker()
        clearMarkerWritten = true
        val failures = ArrayList<IOException>()
        listOfNotNull(external, fallback).distinctBy { it.directory.absolutePath }.forEach { storage ->
            if (
                storage === external &&
                (!storage.directory.isDirectory || !storage.directory.canWrite())
            ) {
                failures += IOException("External task storage is unavailable")
                return@forEach
            }
            try {
                storage.clear()
            } catch (error: IOException) {
                failures += error
            } catch (error: IllegalArgumentException) {
                failures += IOException("Cannot clear task storage", error)
            }
        }
        if (failures.isEmpty()) {
            deleteRequired(clearMarker())
            clearMarkerWritten = false
        } else {
            throw failures.first()
        }
    }

    private fun ensureWritable() {
        if (clearMarkerWritten || clearMarker().isFile) {
            throw IOException("Task storage clear is incomplete")
        }
    }

    private fun migrateFallback(target: DirectoryTaskStorage): Boolean {
        if (!fallbackDirectory.exists() || !fallbackDirectory.isDirectory) return true
        val sourceNames = fallback.names()
        if (sourceNames.isEmpty()) {
            fallbackDirectory.delete()
            return true
        }
        return try {
            val targetNames = target.names()
            if (targetNames.isNotEmpty() && !mergeFallback(sourceNames, targetNames, target)) {
                return false
            }
            if (targetNames.isEmpty()) {
                sourceNames.forEach { name ->
                    val content = fallback.read(name)
                        ?: throw IOException("Cannot read fallback task data")
                    target.write(name, content)
                }
            }
            if (!mergeFallback(sourceNames, target.names(), target)) return false
            try {
                cleanupFallback(sourceNames)
            } catch (error: IOException) {
                return true
            }
            true
        } catch (error: IOException) {
            false
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    private fun mergeFallback(
        sourceNames: List<String>,
        targetNames: List<String>,
        target: DirectoryTaskStorage
    ): Boolean {
        if (targetNames.isEmpty()) {
            return sourceNames.all { name -> target.read(name) == fallback.read(name) }
        }
        val sourceMetadata = fallback.read(METADATA_FILE)?.let(::decodeMetadata) ?: return false
        val targetMetadata = target.read(METADATA_FILE)?.let(::decodeMetadata) ?: return false
        val preferredMetadata = if (sourceMetadata.revision > targetMetadata.revision) {
            sourceMetadata
        } else {
            targetMetadata
        }
        val otherMetadata = if (preferredMetadata === sourceMetadata) targetMetadata else sourceMetadata
        val sourceTasks = sourceNames.filter(::isTaskName).mapNotNull { name ->
            fallback.read(name)?.let(::decodeTaskStamp)?.let { stamp -> name to stamp }
        }.toMap()
        val targetTasks = targetNames.filter(::isTaskName).mapNotNull { name ->
            target.read(name)?.let(::decodeTaskStamp)?.let { stamp -> name to stamp }
        }.toMap()
        val mergedTasks = mergeTasks(
            sourceMetadata = sourceMetadata,
            sourceTasks = sourceTasks,
            targetMetadata = targetMetadata,
            targetTasks = targetTasks,
            deletedTaskIds = if (sourceMetadata.revision == targetMetadata.revision) {
                sourceMetadata.deletedTaskIds + targetMetadata.deletedTaskIds
            } else {
                preferredMetadata.deletedTaskIds
            },
            target = target
        )
        val taskIds = mergedTasks.keys.map { it.removeSuffix(JSON_SUFFIX) }.sorted()
        val lists = mergeLists(
            preferred = preferredMetadata,
            other = otherMetadata,
            deletedListIds = if (sourceMetadata.revision == targetMetadata.revision) {
                sourceMetadata.deletedListIds + targetMetadata.deletedListIds
            } else {
                preferredMetadata.deletedListIds
            }
        )
        val deletedTasks = if (sourceMetadata.revision == targetMetadata.revision) {
            sourceMetadata.deletedTaskIds + targetMetadata.deletedTaskIds
        } else {
            preferredMetadata.deletedTaskIds
        }.filterNot(taskIds::contains)
        val listIds = lists.mapNotNull { element -> element.idOrNull() }.toSet()
        val deletedLists = if (sourceMetadata.revision == targetMetadata.revision) {
            sourceMetadata.deletedListIds + targetMetadata.deletedListIds
        } else {
            preferredMetadata.deletedListIds
        }.filterNot(listIds::contains)
        val mergedMetadata = JsonObject(
            preferredMetadata.raw + mapOf(
                LISTS_KEY to JsonArray(lists),
                TASK_IDS_KEY to JsonArray(taskIds.map(::JsonPrimitive)),
                DELETED_TASK_IDS_KEY to JsonArray(deletedTasks.distinct().sorted().map(::JsonPrimitive)),
                DELETED_LIST_IDS_KEY to JsonArray(deletedLists.distinct().sorted().map(::JsonPrimitive)),
                REVISION_KEY to JsonPrimitive(maxOf(sourceMetadata.revision, targetMetadata.revision))
            )
        ).toString()
        target.write(METADATA_FILE, mergedMetadata)
        targetTasks.keys.filterNot(mergedTasks::containsKey).forEach(target::delete)
        return mergedTasks.all { (name, content) -> target.read(name) == content }
    }

    private fun mergeTasks(
        sourceMetadata: MetadataStamp,
        sourceTasks: Map<String, TaskStamp>,
        targetMetadata: MetadataStamp,
        targetTasks: Map<String, TaskStamp>,
        deletedTaskIds: Set<String>,
        target: DirectoryTaskStorage
    ): Map<String, String> {
        val names = LinkedHashSet<String>()
        names += sourceTasks.keys
        names += targetTasks.keys
        val merged = LinkedHashMap<String, String>()
        names.forEach { name ->
            val id = name.removeSuffix(JSON_SUFFIX)
            if (id in deletedTaskIds) return@forEach
            val sourcePresent = sourceMetadata.includesTask(id, name in sourceTasks)
            val targetPresent = targetMetadata.includesTask(id, name in targetTasks)
            val content = when {
                sourcePresent && targetPresent -> {
                    val sourceStamp = requireNotNull(sourceTasks[name])
                    val targetStamp = requireNotNull(targetTasks[name])
                    if (sourceStamp.updatedAtMillis >= targetStamp.updatedAtMillis) {
                        fallback.read(name)
                    } else {
                        target.read(name)
                    }
                }
                sourcePresent -> fallback.read(name)
                targetPresent -> target.read(name)
                else -> null
            }
            if (content != null) {
                merged[name] = content
                if (target.read(name) != content) target.write(name, content)
            }
        }
        return merged
    }

    private fun mergeLists(
        preferred: MetadataStamp,
        other: MetadataStamp,
        deletedListIds: Set<String>
    ): List<JsonElement> {
        val byId = LinkedHashMap<String, JsonElement>()
        preferred.lists.forEach { element ->
            element.idOrNull()?.takeIf { it !in deletedListIds }?.let { id -> byId[id] = element }
        }
        other.lists.forEach { element ->
            val id = element.idOrNull() ?: return@forEach
            if (id !in byId && id !in deletedListIds) byId[id] = element
        }
        return byId.values.toList()
    }

    private fun decodeMetadata(raw: String): MetadataStamp? = runCatching {
        val value = Json.parseToJsonElement(raw).jsonObject
        val lists = value[LISTS_KEY] as? JsonArray ?: JsonArray(emptyList())
        val taskIds = (value[TASK_IDS_KEY] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
        val deletedTaskIds = (value[DELETED_TASK_IDS_KEY] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
        val deletedListIds = (value[DELETED_LIST_IDS_KEY] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
        MetadataStamp(
            raw = value,
            lists = lists,
            taskIds = taskIds,
            deletedTaskIds = deletedTaskIds,
            deletedListIds = deletedListIds,
            revision = value[REVISION_KEY]?.jsonPrimitive?.longOrNull ?: 0L
        )
    }.getOrNull()

    private fun decodeTaskStamp(raw: String): TaskStamp? = runCatching {
        val value = Json.parseToJsonElement(raw).jsonObject
        TaskStamp(value[UPDATED_AT_KEY]?.jsonPrimitive?.longOrNull ?: 0L)
    }.getOrNull()

    private fun JsonElement.idOrNull(): String? =
        runCatching { jsonObject[ID_KEY]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun isTaskName(name: String): Boolean =
        name.startsWith(TASK_PREFIX) && name.endsWith(JSON_SUFFIX)

    private fun MetadataStamp.includesTask(id: String, hasFile: Boolean): Boolean {
        if (!hasFile || id in deletedTaskIds) return false
        return taskIds?.contains(id) ?: true
    }

    private fun cleanupFallback(names: List<String>) {
        names.filterNot { it == METADATA_FILE }.forEach(fallback::delete)
        if (METADATA_FILE in names) fallback.delete(METADATA_FILE)
        fallbackDirectory.delete()
    }

    private fun applyPendingClear() {
        val marker = clearMarker()
        if (!marker.isFile) return
        val pending = runCatching {
            Json.decodeFromString(ClearMarker.serializer(), marker.readText(Charsets.UTF_8))
        }.getOrElse {
            throw IOException("Cannot read task clear marker", it)
        }
        val remaining = pending.directories.filter { path ->
            val directory = File(path)
            val available = when {
                external != null && directory == external.directory -> prepareDirectory(directory)
                directory == fallback.directory -> prepareDirectory(directory)
                else -> false
            }
            if (!available) return@filter true
            try {
                DirectoryTaskStorage(directory).clear()
                false
            } catch (error: IOException) {
                true
            }
        }
        if (remaining.isEmpty()) {
            deleteRequired(marker)
        } else {
            writeClearMarker(remaining)
        }
    }

    private fun writeClearMarker(
        directories: List<String> = listOfNotNull(
            external?.directory?.canonicalPath,
            fallback.directory.canonicalPath
        ).distinct()
    ) {
        val marker = clearMarker()
        val temporary = File(marker.parentFile, marker.name + ".tmp")
        marker.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs()) {
                throw IOException("Cannot create task clear marker")
            }
        }
        FileOutputStream(temporary).use { stream ->
            stream.write(
                Json.encodeToString(ClearMarker.serializer(), ClearMarker(directories))
                    .toByteArray(Charsets.UTF_8)
            )
            stream.flush()
            stream.fd.sync()
        }
        deleteRequired(marker)
        if (!temporary.renameTo(marker)) throw IOException("Cannot write task clear marker")
    }

    private fun clearMarker(): File = File(fallbackDirectory.parentFile, CLEAR_MARKER_FILE)

    private fun prepareDirectory(directory: File): Boolean =
        (directory.isDirectory || directory.mkdirs()) && directory.canWrite()

    private fun deleteRequired(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Cannot delete task clear marker")
    }

    companion object {
        const val DIRECTORY_NAME = "tasks"
        const val METADATA_FILE = "metadata.json"
        const val LISTS_KEY = "lists"
        const val TASK_IDS_KEY = "taskIds"
        const val DELETED_TASK_IDS_KEY = "deletedTaskIds"
        const val DELETED_LIST_IDS_KEY = "deletedListIds"
        const val REVISION_KEY = "revision"
        const val UPDATED_AT_KEY = "updatedAtMillis"
        const val ID_KEY = "id"
        const val TASK_PREFIX = "task-"
        const val JSON_SUFFIX = ".json"
        const val CLEAR_MARKER_FILE = "tasks-clear.json"
    }

    @Serializable
    private data class ClearMarker(val directories: List<String>)

    private data class MetadataStamp(
        val raw: JsonObject,
        val lists: List<JsonElement>,
        val taskIds: Set<String>?,
        val deletedTaskIds: Set<String>,
        val deletedListIds: Set<String>,
        val revision: Long
    )

    private data class TaskStamp(val updatedAtMillis: Long)
}

class DirectoryTaskStorage(internal val directory: File) : TaskStorage {

    private val lock = Any()

    override fun read(name: String): String? = synchronized(lock) {
        val target = target(name)
        restoreBackup(target)
        if (!target.isFile) return null
        try {
            target.readText(Charsets.UTF_8)
        } catch (error: IOException) {
            null
        }
    }

    override fun write(name: String, content: String) {
        synchronized(lock) {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create task storage")
            }
            val target = target(name)
            val temporary = temporary(target)
            val backup = backup(target)
            FileOutputStream(temporary).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            var backedUp = false
            try {
                if (target.exists()) {
                    deleteRequired(backup)
                    moveRequired(target, backup)
                    backedUp = true
                }
                moveRequired(temporary, target)
                syncDirectory()
                if (backedUp) deleteRequired(backup)
            } catch (error: IOException) {
                if (backedUp && backup.exists()) {
                    runCatching {
                        deleteRequired(target)
                        moveRequired(backup, target)
                    }
                }
                throw error
            } finally {
                runCatching { deleteRequired(temporary) }
                if (target.exists()) runCatching { deleteRequired(copy(target)) }
            }
        }
    }

    override fun delete(name: String) {
        synchronized(lock) {
            val target = target(name)
            deleteRequired(target)
            deleteRequired(temporary(target))
            deleteRequired(backup(target))
            deleteRequired(copy(target))
            deleteRequired(staged(target))
        }
    }

    override fun names(): List<String> = synchronized(lock) {
        if (!directory.isDirectory) return emptyList()
        recoverFiles()
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    !it.name.endsWith(TEMP_SUFFIX) &&
                    !it.name.endsWith(BACKUP_SUFFIX) &&
                    !it.name.endsWith(COPY_SUFFIX) &&
                    !it.name.endsWith(STAGED_SUFFIX) &&
                    it.name != TRANSACTION_FILE
            }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    override fun commit(writes: Map<String, String>, deletes: Set<String>) {
        synchronized(lock) {
            if (writes.isEmpty() && deletes.isEmpty()) return
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create task storage")
            }
            val stagedFiles = LinkedHashMap<String, File>()
            try {
                writes.forEach { (name, content) ->
                    val target = target(name)
                    val stage = staged(target)
                    stagedFiles[name] = stage
                    FileOutputStream(stage).use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                        stream.flush()
                        stream.fd.sync()
                    }
                }
                deletes.forEach { name -> target(name) }
            } catch (error: IOException) {
                stagedFiles.values.forEach { stage -> runCatching { deleteRequired(stage) } }
                throw error
            }
            val pending = StorageTransaction(
                writes = writes.keys.sorted(),
                deletes = deletes.sorted(),
                digests = writes.mapValues { (_, content) -> sha256(content) }
            )
            val transaction = transactionFile()
            val transactionTemporary = File(directory, TRANSACTION_FILE + TEMP_SUFFIX)
            try {
                FileOutputStream(transactionTemporary).use { stream ->
                    stream.write(
                        Json.encodeToString(StorageTransaction.serializer(), pending)
                            .toByteArray(Charsets.UTF_8)
                    )
                    stream.flush()
                    stream.fd.sync()
                }
                deleteRequired(transaction)
                if (!transactionTemporary.renameTo(transaction)) {
                    throw IOException("Cannot create task transaction")
                }
                syncDirectory()
                finishTransaction(transaction, pending)
            } catch (error: IOException) {
                if (!transaction.isFile) {
                    stagedFiles.values.forEach { stage -> runCatching { deleteRequired(stage) } }
                    runCatching { deleteRequired(transactionTemporary) }
                }
                throw error
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            if (!directory.exists()) return
            val files = directory.listFiles()
                ?: throw IOException("Cannot list task storage")
            files.filter { it.isFile }.forEach(::deleteRequired)
            if (directory.exists() && !directory.delete()) {
                throw IOException("Cannot delete task storage")
            }
        }
    }

    private fun recoverFiles() {
        recoverTransaction()
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(COPY_SUFFIX) }?.forEach { copy ->
            val target = File(directory, copy.name.removeSuffix(COPY_SUFFIX))
            when {
                target.name.endsWith(BACKUP_SUFFIX) || target.name.endsWith(TEMP_SUFFIX) ->
                    deleteRequired(copy)
                target.exists() -> deleteRequired(copy)
                else -> if (!copy.renameTo(target)) throw IOException("Cannot recover task data")
            }
        }
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(BACKUP_SUFFIX) }?.forEach { backup ->
            val target = File(directory, backup.name.removeSuffix(BACKUP_SUFFIX))
            val temporary = temporary(target)
            if (temporary.exists()) {
                deleteRequired(target)
                moveRequired(backup, target)
                deleteRequired(temporary)
            } else if (target.exists()) {
                deleteRequired(backup)
            } else {
                moveRequired(backup, target)
            }
        }
    }

    private fun recoverTransaction() {
        val transaction = transactionFile()
        if (!transaction.isFile) {
            directory.listFiles()?.filter { it.isFile && it.name.endsWith(STAGED_SUFFIX) }
                ?.forEach(::deleteRequired)
            return
        }
        finishTransaction(transaction, decodeTransaction(transaction))
    }

    private fun decodeTransaction(transaction: File): StorageTransaction = runCatching {
        Json.decodeFromString(
            StorageTransaction.serializer(),
            transaction.readText(Charsets.UTF_8)
        )
    }.getOrElse {
        throw IOException("Cannot read task transaction", it)
    }

    private fun finishTransaction(
        transaction: File,
        pending: StorageTransaction
    ) {
        pending.writes.filterNot { it == AppTaskStorage.METADATA_FILE }.forEach { name ->
            commitStaged(name, pending.digests[name])
        }
        if (AppTaskStorage.METADATA_FILE in pending.writes) {
            commitStaged(
                AppTaskStorage.METADATA_FILE,
                pending.digests[AppTaskStorage.METADATA_FILE]
            )
        }
        pending.deletes.filterNot(pending.writes::contains).forEach { name ->
            deleteRequired(target(name))
        }
        syncDirectory()
        deleteRequired(transaction)
    }

    private fun commitStaged(name: String, expectedDigest: String?) {
        val target = target(name)
        val stage = staged(target)
        if (stage.exists()) {
            deleteRequired(target)
            if (!stage.renameTo(target)) throw IOException("Cannot commit task data")
        } else if (!target.isFile || expectedDigest != null && sha256(target) != expectedDigest) {
            throw IOException("Missing staged task data")
        }
    }

    private fun sha256(content: String): String = sha256(content.toByteArray(Charsets.UTF_8))

    private fun sha256(file: File): String = sha256(file.readBytes())

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { byte ->
            byte.toInt().and(0xff).toString(16).padStart(2, '0')
        }

    private fun restoreBackup(target: File) {
        val backup = backup(target)
        val temporary = temporary(target)
        if (backup.exists() && temporary.exists()) {
            deleteRequired(target)
            moveRequired(backup, target)
            deleteRequired(temporary)
        } else if (!target.exists() && backup.exists()) {
            moveRequired(backup, target)
        }
    }

    private fun moveRequired(source: File, target: File) {
        if (source.renameTo(target)) return
        val copy = copy(target)
        deleteRequired(copy)
        FileInputStream(source).use { input ->
            FileOutputStream(copy).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        deleteRequired(target)
        if (!copy.renameTo(target)) throw IOException("Cannot replace task data")
        deleteRequired(source)
    }

    private fun deleteRequired(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Cannot delete task data")
    }

    private fun syncDirectory() {
        runCatching { FileInputStream(directory).use { it.fd.sync() } }
    }

    private fun temporary(target: File): File = File(directory, target.name + TEMP_SUFFIX)

    private fun backup(target: File): File = File(directory, target.name + BACKUP_SUFFIX)

    private fun copy(target: File): File = File(directory, target.name + COPY_SUFFIX)

    private fun staged(target: File): File = File(directory, target.name + STAGED_SUFFIX)

    private fun transactionFile(): File = File(directory, TRANSACTION_FILE)

    private fun target(name: String): File {
        require(name.isNotEmpty() && name != "." && name != "..")
        require(name.none { it == '/' || it == '\\' })
        return File(directory, name)
    }

    @Serializable
    private data class StorageTransaction(
        val writes: List<String>,
        val deletes: List<String>,
        val digests: Map<String, String> = emptyMap()
    )

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
        const val BACKUP_SUFFIX = ".bak"
        const val COPY_SUFFIX = ".copy"
        const val STAGED_SUFFIX = ".staged"
        const val TRANSACTION_FILE = ".transaction.json"
    }
}

class InMemoryTaskStorage : TaskStorage {

    private val entries = LinkedHashMap<String, String>()

    override fun read(name: String): String? = synchronized(entries) { entries[name] }

    override fun write(name: String, content: String) {
        synchronized(entries) { entries[name] = content }
    }

    override fun delete(name: String) {
        synchronized(entries) { entries.remove(name) }
    }

    override fun names(): List<String> = synchronized(entries) { entries.keys.sorted() }

    override fun clear() {
        synchronized(entries) { entries.clear() }
    }
}
