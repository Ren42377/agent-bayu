package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.platform.files.FileAccess
import dev.agentbayu.app.platform.files.FileAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ListFilesTool(private val files: () -> FileAccess) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "List one folder in the owner shared storage. A path without a leading " +
            "slash is read from the storage root, and \".\" is the root itself.",
        parameters = toolSchema(
            ToolField(
                name = "path",
                type = "string",
                description = "Folder to list, for example . or Download or /sdcard/DCIM",
                required = false
            ),
            ToolField(
                name = "limit",
                type = "integer",
                description = "How many entries to report at most",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val path = arguments.text("path") ?: STORAGE_ROOT
        val limit = arguments.number("limit", FileAccess.MAX_ENTRIES)
            .coerceIn(1, FileAccess.MAX_ENTRIES)
        val access = files()
        try {
            call.reply(clipOutput(access.list(path, limit)), displayPath = access.resolve(path).path)
        } catch (error: FileAccessException) {
            call.problem(error.message.orEmpty())
        }
    }

    private companion object {
        const val NAME = "list_files"
    }
}

class ReadFileTool(private val files: () -> FileAccess) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Read one text file, such as a note, a log, or source code. Binary files " +
            "are refused, so use view_image for pictures.",
        parameters = toolSchema(
            ToolField("path", "string", "File to read"),
            ToolField(
                name = "max_bytes",
                type = "integer",
                description = "Refuse the file when it is larger than this",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val path = arguments.text("path")
            ?: return@withContext call.problem("A path is required")
        val maxBytes = arguments.number("max_bytes", DEFAULT_READ_BYTES)
            .coerceIn(1, FileAccess.MAX_READ_BYTES)
        val access = files()
        try {
            call.reply(clipOutput(access.read(path, maxBytes)), displayPath = access.resolve(path).path)
        } catch (error: FileAccessException) {
            call.problem(error.message.orEmpty())
        }
    }

    private companion object {
        const val NAME = "read_file"
        const val DEFAULT_READ_BYTES = 64 * 1024
    }
}

class SearchFilesTool(private val files: () -> FileAccess) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Search text files under a folder and report every matching line with " +
            "its path and line number.",
        parameters = toolSchema(
            ToolField("query", "string", "Text to look for, matched without case"),
            ToolField(
                name = "path",
                type = "string",
                description = "Folder to search, defaults to the storage root",
                required = false
            ),
            ToolField(
                name = "extension",
                type = "string",
                description = "Only look at files with this extension, for example txt or py",
                required = false
            ),
            ToolField(
                name = "limit",
                type = "integer",
                description = "How many matching lines to report at most",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val query = arguments.text("query")
            ?: return@withContext call.problem("A query is required")
        val path = arguments.text("path") ?: STORAGE_ROOT
        val limit = arguments.number("limit", FileAccess.MAX_MATCHES)
            .coerceIn(1, FileAccess.MAX_MATCHES)
        val job = currentCoroutineContext()[Job]
        val access = files()
        try {
            call.reply(
                clipOutput(
                    access.search(
                        path = path,
                        query = query,
                        extension = arguments.text("extension"),
                        limit = limit,
                        active = { job?.isActive != false }
                    )
                ),
                displayPath = access.resolve(path).path
            )
        } catch (error: FileAccessException) {
            currentCoroutineContext().ensureActive()
            call.problem(error.message.orEmpty())
        }
    }

    private companion object {
        const val NAME = "search_files"
    }
}

internal const val STORAGE_ROOT = "."

internal const val MAX_OUTPUT_CHARS = 24_000

internal fun clipOutput(text: String, limit: Int = MAX_OUTPUT_CHARS): String =
    if (text.length <= limit) text else text.take(limit) + CLIPPED_NOTE

private const val CLIPPED_NOTE = "\n[output clipped]"
