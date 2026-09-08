package dev.agentbayu.app.domain.tasks

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.platform.EncryptedStorage
import dev.agentbayu.app.platform.TaskStorage
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class TaskStore(
    private val storage: TaskStorage,
    private val legacyStorage: EncryptedStorage? = null,
    private val clock: Clock = RealClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    constructor(
        storage: TaskStorage,
        clock: Clock = RealClock,
        zone: () -> ZoneId = { ZoneId.systemDefault() }
    ) : this(storage, null, clock, zone)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val loadResult = load()
    private val restored = loadResult.file
    private val listsState = MutableStateFlow(restored.lists)
    private val tasksState = MutableStateFlow(restored.tasks)
    private val sortState = MutableStateFlow(restored.sort)
    private val activeState = MutableStateFlow(
        restored.activeListId ?: restored.lists.firstOrNull()?.id
    )
    private var revision = restored.revision
    private var persistedTasks = loadResult.persistedTasks
    private var persistedMetadata = loadResult.persistedMetadata
    private var deletedTaskIds = restored.deletedTaskIds.toSet()
    private var deletedListIds = restored.deletedListIds.toSet()

    val lists: StateFlow<List<TaskList>> = listsState.asStateFlow()
    val tasks: StateFlow<List<TaskItem>> = tasksState.asStateFlow()
    val sort: StateFlow<TaskSort> = sortState.asStateFlow()
    val activeListId: StateFlow<String?> = activeState.asStateFlow()

    init {
        if (loadResult.repairMetadata) persistMetadata()
    }

    @Synchronized
    fun find(taskId: String): TaskItem? = tasksState.value.firstOrNull { it.id == taskId }

    @Synchronized
    fun findList(listId: String): TaskList? = listsState.value.firstOrNull { it.id == listId }

    @Synchronized
    fun createList(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return ""
        val id = newId(LIST_PREFIX) { candidate -> listsState.value.any { it.id == candidate } }
        val position = listsState.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        listsState.value = listsState.value + TaskList(
            id = id,
            title = trimmed,
            position = position,
            createdAtMillis = clock.nowMillis()
        )
        deletedListIds -= id
        if (activeState.value == null) activeState.value = id
        persist()
        return id
    }

    @Synchronized
    fun renameList(listId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val current = listsState.value
        val index = current.indexOfFirst { it.id == listId }
        if (index < 0 || current[index].title == trimmed) return
        listsState.value = current.toMutableList().apply {
            set(index, current[index].copy(title = trimmed))
        }
        persist()
    }

    @Synchronized
    fun removeList(listId: String) {
        val remaining = listsState.value.filterNot { it.id == listId }
        if (remaining.size == listsState.value.size) return
        val removedTaskIds = tasksState.value
            .filter { it.listId == listId }
            .mapTo(HashSet()) { it.id }
        listsState.value = remaining
        tasksState.value = tasksState.value.filterNot { it.listId == listId }
        deletedListIds += listId
        deletedTaskIds += removedTaskIds
        if (activeState.value == listId) activeState.value = remaining.firstOrNull()?.id
        persist()
    }

    @Synchronized
    fun reorderLists(orderedIds: List<String>) {
        val ranks = orderedIds.withIndex().associate { (index, id) -> id to index }
        listsState.value = listsState.value
            .map { list -> ranks[list.id]?.let { list.copy(position = it) } ?: list }
        persist()
    }

    @Synchronized
    fun setActiveList(listId: String) {
        if (activeState.value == listId || findList(listId) == null) return
        activeState.value = listId
        persist()
    }

    @Synchronized
    fun setSort(sort: TaskSort) {
        if (sortState.value == sort) return
        sortState.value = sort
        persist()
    }

    @Synchronized
    fun createTask(listId: String, title: String, parentId: String? = null): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || findList(listId) == null) return ""
        val validParent = parentId?.takeIf { id ->
            find(id)?.let { parent -> parent.listId == listId && parent.parentId == null } == true
        }
        val now = clock.nowMillis()
        val id = newId(TASK_PREFIX) { candidate -> tasksState.value.any { it.id == candidate } }
        tasksState.value = tasksState.value + TaskItem(
            id = id,
            listId = listId,
            parentId = validParent,
            title = trimmed,
            position = nextPosition(listId, validParent),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        deletedTaskIds -= id
        persist()
        return id
    }

    @Synchronized
    fun upsertTask(task: TaskItem) {
        if (!VALID_TASK_ID.matches(task.id) || findList(task.listId) == null) return
        val parentId = task.parentId?.takeIf { id ->
            id != task.id && find(id)?.let { parent ->
                parent.listId == task.listId && parent.parentId == null
            } == true
        }
        val now = clock.nowMillis()
        val current = tasksState.value
        val index = current.indexOfFirst { it.id == task.id }
        tasksState.value = if (index >= 0) {
            current.toMutableList().apply {
                set(index, task.copy(parentId = parentId, updatedAtMillis = now))
            }
        } else {
            current + task.copy(
                parentId = parentId,
                position = nextPosition(task.listId, parentId),
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
        deletedTaskIds -= task.id
        persist()
    }

    @Synchronized
    fun removeTask(taskId: String) {
        val removed = descendantIds(taskId)
        if (removed.isEmpty()) return
        tasksState.value = tasksState.value.filterNot { it.id in removed }
        deletedTaskIds += removed
        persist()
    }

    @Synchronized
    fun clearCompleted(listId: String) {
        val completed = tasksState.value
            .filter { it.listId == listId && it.completed }
            .mapTo(HashSet()) { it.id }
        if (completed.isEmpty()) return
        val removed = HashSet(completed)
        var changed: Boolean
        do {
            changed = removed.addAll(
                tasksState.value.filter { it.parentId in removed }.map { it.id }
            )
        } while (changed)
        tasksState.value = tasksState.value.filterNot { it.id in removed }
        deletedTaskIds += removed
        persist()
    }

    @Synchronized
    fun setCompleted(taskId: String, completed: Boolean) {
        val target = find(taskId) ?: return
        if (target.completed == completed) return
        val now = clock.nowMillis()
        if (!completed) {
            tasksState.value = tasksState.value.map { item ->
                when {
                    item.id == taskId -> item.reopened(now)
                    item.id == target.parentId && item.completed -> item.reopened(now)
                    else -> item
                }
            }
            persist()
            return
        }
        val rolled = target.repeat?.let { rollForward(target, it, now) }
        tasksState.value = if (rolled != null) {
            val history = target.copy(
                id = newId(TASK_PREFIX) { candidate -> tasksState.value.any { it.id == candidate } },
                repeat = null,
                completed = true,
                completedAtMillis = now,
                updatedAtMillis = now
            )
            tasksState.value.map { if (it.id == taskId) rolled else it } + history
        } else {
            val completedIds = descendantIds(taskId)
            tasksState.value.map { item ->
                if (item.id in completedIds) {
                    if (item.completed) item else item.completedAt(now)
                } else {
                    item
                }
            }
        }
        persist()
    }

    @Synchronized
    fun indent(taskId: String) {
        val target = find(taskId) ?: return
        if (target.parentId != null) return
        val siblings = sortTasks(
            tasksState.value.filter { it.listId == target.listId && it.parentId == null },
            TaskSort.MY_ORDER
        )
        val index = siblings.indexOfFirst { it.id == taskId }
        val parent = siblings.getOrNull(index - 1) ?: return
        val moved = listOf(target) + sortTasks(
            tasksState.value.filter { it.parentId == taskId },
            TaskSort.MY_ORDER
        )
        val base = nextPosition(target.listId, parent.id)
        val positions = moved.withIndex().associate { (offset, item) -> item.id to base + offset }
        val now = clock.nowMillis()
        tasksState.value = tasksState.value.map { item ->
            if (item.id == parent.id) {
                return@map if (item.repeat == null) item else item.copy(
                    repeat = null,
                    updatedAtMillis = now
                )
            }
            val position = positions[item.id] ?: return@map item
            item.copy(
                parentId = parent.id,
                position = position,
                repeat = null,
                updatedAtMillis = now
            )
        }
        persist()
    }

    @Synchronized
    fun setStarred(taskId: String, starred: Boolean) {
        val target = find(taskId) ?: return
        if (target.starred == starred) return
        val now = clock.nowMillis()
        replace(
            target.copy(
                starred = starred,
                starredAtMillis = if (starred) now else null,
                updatedAtMillis = now
            )
        )
    }

    @Synchronized
    fun move(taskId: String, delta: Int) {
        val target = find(taskId) ?: return
        val siblings = sortTasks(
            tasksState.value.filter {
                it.listId == target.listId && it.parentId == target.parentId && !it.completed
            },
            TaskSort.MY_ORDER
        ).toMutableList()
        val from = siblings.indexOfFirst { it.id == taskId }
        val to = from + delta
        if (from < 0 || to < 0 || to >= siblings.size) return
        siblings.add(to, siblings.removeAt(from))
        val positions = siblings.withIndex().associate { (index, item) -> item.id to index }
        val now = clock.nowMillis()
        tasksState.value = tasksState.value.map { item ->
            val position = positions[item.id] ?: return@map item
            if (item.position == position) item else item.copy(
                position = position,
                updatedAtMillis = now
            )
        }
        persist()
    }

    @Synchronized
    fun moveToList(taskId: String, listId: String) {
        val target = find(taskId) ?: return
        if (target.listId == listId || findList(listId) == null) return
        val movedIds = descendantIds(taskId)
        val now = clock.nowMillis()
        val position = nextPosition(listId, null)
        tasksState.value = tasksState.value.map { item ->
            when {
                item.id == taskId -> item.copy(
                    listId = listId,
                    parentId = null,
                    position = position,
                    updatedAtMillis = now
                )
                item.id in movedIds -> item.copy(listId = listId, updatedAtMillis = now)
                else -> item
            }
        }
        persist()
    }

    @Synchronized
    fun unindent(taskId: String) {
        val target = find(taskId) ?: return
        val parent = target.parentId?.let { find(it) } ?: return
        val position = parent.position + 1
        val now = clock.nowMillis()
        tasksState.value = tasksState.value.map { item ->
            when {
                item.id == taskId -> item.copy(
                    parentId = null,
                    position = position,
                    updatedAtMillis = now
                )
                item.listId == target.listId && item.parentId == null && item.position >= position ->
                    item.copy(position = item.position + 1, updatedAtMillis = now)
                else -> item
            }
        }
        persist()
    }

    @Synchronized
    fun clear() {
        storage.clear()
        legacyStorage?.delete(FILE_NAME)
        listsState.value = emptyList()
        tasksState.value = emptyList()
        sortState.value = TaskSort.MY_ORDER
        activeState.value = null
        revision = 0L
        persistedTasks = emptyMap()
        persistedMetadata = null
        deletedTaskIds = emptySet()
        deletedListIds = emptySet()
    }

    private fun replace(task: TaskItem) {
        val current = tasksState.value
        val index = current.indexOfFirst { it.id == task.id }
        if (index < 0) return
        tasksState.value = current.toMutableList().apply { set(index, task) }
        persist()
    }

    private fun TaskItem.reopened(now: Long): TaskItem =
        copy(completed = false, completedAtMillis = null, updatedAtMillis = now)

    private fun TaskItem.completedAt(now: Long): TaskItem =
        copy(completed = true, completedAtMillis = now, updatedAtMillis = now)

    private fun rollForward(task: TaskItem, repeat: TaskRepeat, now: Long): TaskItem? {
        if (task.parentId != null) return null
        if (tasksState.value.any { it.parentId == task.id }) return null
        var candidate = task.scheduleAtMillis ?: return null
        var occurrence = task.occurrenceIndex
        var steps = 0
        do {
            candidate = nextOccurrence(repeat, candidate, zone())
            occurrence += 1
            steps += 1
            if (isSeriesFinished(repeat, occurrence, candidate)) return null
        } while (candidate <= now && steps < MAX_ROLL_STEPS)
        if (candidate <= now) return null
        return if (task.dueAtMillis != null) {
            task.copy(dueAtMillis = candidate, occurrenceIndex = occurrence, updatedAtMillis = now)
        } else {
            task.copy(
                deadlineAtMillis = candidate,
                occurrenceIndex = occurrence,
                updatedAtMillis = now
            )
        }
    }

    private fun descendantIds(taskId: String): Set<String> {
        if (find(taskId) == null) return emptySet()
        val result = linkedSetOf(taskId)
        var changed: Boolean
        do {
            changed = result.addAll(
                tasksState.value.filter { it.parentId in result }.map { it.id }
            )
        } while (changed)
        return result
    }

    private fun nextPosition(listId: String, parentId: String?): Int = tasksState.value
        .filter { it.listId == listId && it.parentId == parentId }
        .maxOfOrNull { it.position }
        ?.plus(1)
        ?: 0

    private fun newId(prefix: String, taken: (String) -> Boolean): String {
        val stamp = prefix + clock.nowMillis().toString(RADIX)
        if (!taken(stamp)) return stamp
        var suffix = 1
        while (taken(stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX))) suffix += 1
        return stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX)
    }

    private fun load(): LoadResult {
        val jsonResult = loadJsonFiles()
        if (jsonResult != null && jsonResult.metadataValid) {
            return jsonResult.toLoadResult()
        }
        val legacy = legacyStorage?.read(FILE_NAME)?.let(::decodeLegacy)
        if (legacy != null && (jsonResult == null || !jsonResult.metadataValid)) {
            return migrateLegacy(legacy)
        }
        if (jsonResult != null) return jsonResult.toLoadResult()
        return LoadResult(TaskFile(), emptyMap(), null, repairMetadata = false)
    }

    private fun loadJsonFiles(): JsonLoad? {
        val taskNames = storage.names().filter(::isTaskFileName)
        val metadataRaw = storage.read(METADATA_FILE)
        if (metadataRaw == null && taskNames.isEmpty()) return null
        val decodedMetadata = metadataRaw?.let(::decodeMetadata)
        val metadata = decodedMetadata ?: TaskMetadata()
        val allowedTaskIds = decodedMetadata?.taskIds?.toSet()
        val tasks = taskNames.mapNotNull { name ->
            val task = storage.read(name)?.let(::decodeTask) ?: return@mapNotNull null
            if (
                !VALID_TASK_ID.matches(task.id) ||
                taskFileName(task.id) != name ||
                (allowedTaskIds != null && task.id !in allowedTaskIds) ||
                task.id in metadata.deletedTaskIds
            ) {
                null
            } else {
                task
            }
        }
        val sanitized = sanitizeTasks(tasks)
        val lists = recoverLists(
            metadata.lists.filterNot { it.id in metadata.deletedListIds },
            sanitized
        )
        val active = metadata.activeListId?.takeIf { id -> lists.any { it.id == id } }
            ?: lists.firstOrNull()?.id
        val file = TaskFile(
            version = metadata.version,
            revision = metadata.revision,
            lists = lists,
            tasks = sanitized,
            deletedTaskIds = metadata.deletedTaskIds,
            deletedListIds = metadata.deletedListIds,
            sort = metadata.sort,
            activeListId = active
        )
        val metadataValid = decodedMetadata != null
        val metadataMatches = metadataValid && metadata == file.metadata()
        return JsonLoad(
            file = file,
            metadataValid = metadataValid,
            repairMetadata = !metadataMatches,
            persistedTasks = sanitized.associateBy { it.id },
            persistedMetadata = decodedMetadata
        )
    }

    private fun sanitizeTasks(tasks: List<TaskItem>): List<TaskItem> {
        val byId = LinkedHashMap<String, TaskItem>()
        tasks.forEach { task -> if (task.id !in byId) byId[task.id] = task }
        val ids = byId.keys
        return byId.values.map { task ->
            val parent = task.parentId?.let(byId::get)
            if (
                parent == null ||
                parent.id !in ids ||
                parent.id == task.id ||
                parent.listId != task.listId ||
                parent.parentId != null
            ) {
                task.copy(parentId = null)
            } else {
                task
            }
        }
    }

    private fun decodeLegacy(raw: String): TaskFile? = try {
        json.decodeFromString(TaskFile.serializer(), raw)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun decodeMetadata(raw: String): TaskMetadata? = try {
        json.decodeFromString(TaskMetadata.serializer(), raw)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun decodeTask(raw: String): TaskItem? = try {
        json.decodeFromString(TaskItem.serializer(), raw)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun recoverLists(stored: List<TaskList>, tasks: List<TaskItem>): List<TaskList> {
        val recovered = stored.distinctBy { it.id }.toMutableList()
        val existing = recovered.mapTo(HashSet()) { it.id }
        tasks.map { it.listId }.distinct().sorted().forEach { listId ->
            if (listId !in existing) {
                recovered += TaskList(
                    id = listId,
                    title = RECOVERED_LIST_TITLE,
                    position = recovered.size
                )
                existing += listId
            }
        }
        return recovered
    }

    private fun migrateLegacy(file: TaskFile): LoadResult {
        val normalized = file.normalizedForComparison().copy(revision = file.revision + 1L)
        return try {
            storage.clear()
            val writes = normalized.tasks.associate { task ->
                taskFileName(task.id) to json.encodeToString(TaskItem.serializer(), task)
            }.toMutableMap()
            writes[METADATA_FILE] = json.encodeToString(
                TaskMetadata.serializer(),
                normalized.metadata(normalized.tasks.map { it.id })
            )
            storage.commit(writes, emptySet())
            val restored = loadJsonFiles()
            if (restored == null || !sameSnapshot(restored.file, normalized)) {
                cleanupFailedMigration()
                return LoadResult(normalized, emptyMap(), null, repairMetadata = false)
            }
            legacyStorage?.delete(FILE_NAME)
            restored.toLoadResult()
        } catch (error: IOException) {
            cleanupFailedMigration()
            LoadResult(normalized, emptyMap(), null, repairMetadata = false)
        } catch (error: IllegalArgumentException) {
            cleanupFailedMigration()
            LoadResult(normalized, emptyMap(), null, repairMetadata = false)
        }
    }

    private fun cleanupFailedMigration() {
        runCatching { storage.clear() }
    }

    private fun sameSnapshot(actual: TaskFile, expected: TaskFile): Boolean =
        actual.metadata() == expected.metadata() &&
            actual.tasks.associateBy { it.id } == expected.tasks.associateBy { it.id }

    private fun persist() = synchronized(this) {
        val tasks = tasksState.value
        val byId = tasks.associateBy { it.id }
        val previousRevision = revision
        val nextRevision = previousRevision + 1L
        val metadata = currentMetadata(nextRevision, byId.keys)
        try {
            val writes = LinkedHashMap<String, String>()
            byId.forEach { (id, task) ->
                if (persistedTasks[id] != task) {
                    writes[taskFileName(id)] = json.encodeToString(TaskItem.serializer(), task)
                }
            }
            writes[METADATA_FILE] = json.encodeToString(TaskMetadata.serializer(), metadata)
            val deletes = persistedTasks.keys.filterNot(byId::containsKey)
                .mapTo(LinkedHashSet()) { id -> taskFileName(id) }
            storage.commit(writes, deletes)
        } catch (error: IOException) {
            persistedTasks = readPersistedTasks()
            persistedMetadata = storage.read(METADATA_FILE)?.let(::decodeMetadata)
            revision = persistedMetadata?.revision ?: previousRevision
            throw error
        }
        revision = nextRevision
        persistedTasks = byId
        persistedMetadata = metadata
    }

    private fun persistMetadata() = synchronized(this) {
        val metadata = currentMetadata(revision, tasksState.value.map { it.id })
        storage.commit(
            mapOf(METADATA_FILE to json.encodeToString(TaskMetadata.serializer(), metadata)),
            emptySet()
        )
        persistedMetadata = metadata
    }

    private fun readPersistedTasks(): Map<String, TaskItem> = storage.names()
        .filter(::isTaskFileName)
        .mapNotNull { name ->
            val task = storage.read(name)?.let(::decodeTask) ?: return@mapNotNull null
            if (!VALID_TASK_ID.matches(task.id) || taskFileName(task.id) != name) null else task.id to task
        }
        .toMap()

    private fun currentMetadata(revision: Long, taskIds: Collection<String>): TaskMetadata = TaskMetadata(
        revision = revision,
        lists = listsState.value,
        taskIds = taskIds.sorted(),
        deletedTaskIds = deletedTaskIds.sorted(),
        deletedListIds = deletedListIds.sorted(),
        sort = sortState.value,
        activeListId = activeState.value
    )

    private fun TaskFile.metadata(taskIds: List<String>? = tasks.map { it.id }): TaskMetadata = TaskMetadata(
        version = version,
        revision = revision,
        lists = lists,
        taskIds = taskIds?.sorted(),
        deletedTaskIds = deletedTaskIds.sorted(),
        deletedListIds = deletedListIds.sorted(),
        sort = sort,
        activeListId = activeListId
    )

    private fun TaskFile.normalizedForComparison(): TaskFile {
        val validTasks = tasks.filter { VALID_TASK_ID.matches(it.id) }.distinctBy { it.id }
        val sanitized = sanitizeTasks(validTasks)
        val recoveredLists = recoverLists(lists, sanitized)
        return copy(
            lists = recoveredLists,
            tasks = sanitized,
            activeListId = activeListId?.takeIf { id -> recoveredLists.any { it.id == id } }
                ?: recoveredLists.firstOrNull()?.id
        )
    }

    private fun taskFileName(id: String): String {
        require(VALID_TASK_ID.matches(id))
        return id + JSON_SUFFIX
    }

    private fun isTaskFileName(name: String): Boolean {
        if (!name.endsWith(JSON_SUFFIX) || name == METADATA_FILE) return false
        return VALID_TASK_ID.matches(name.removeSuffix(JSON_SUFFIX))
    }

    private data class JsonLoad(
        val file: TaskFile,
        val metadataValid: Boolean,
        val repairMetadata: Boolean,
        val persistedTasks: Map<String, TaskItem>,
        val persistedMetadata: TaskMetadata?
    ) {
        fun toLoadResult(): LoadResult = LoadResult(
            file = file,
            persistedTasks = persistedTasks,
            persistedMetadata = persistedMetadata,
            repairMetadata = repairMetadata
        )
    }

    private data class LoadResult(
        val file: TaskFile,
        val persistedTasks: Map<String, TaskItem>,
        val persistedMetadata: TaskMetadata?,
        val repairMetadata: Boolean
    )

    companion object {
        const val FILE_NAME = "tasks.bin"
        const val METADATA_FILE = "metadata.json"
        private const val JSON_SUFFIX = ".json"
        private const val RECOVERED_LIST_TITLE = "Recovered"
        private val VALID_TASK_ID = Regex("task-[a-z0-9]+(?:-[a-z0-9]+)*")
        private const val LIST_PREFIX = "list-"
        private const val TASK_PREFIX = "task-"
        private const val SUFFIX_SEPARATOR = "-"
        private const val RADIX = 36
        private const val MAX_ROLL_STEPS = 512
    }
}
