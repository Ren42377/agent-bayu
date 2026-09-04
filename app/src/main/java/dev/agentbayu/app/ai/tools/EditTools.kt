package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.domain.tools.DiffLine
import dev.agentbayu.app.domain.tools.TextDiff
import dev.agentbayu.app.domain.tools.ToolApprovalDecision
import dev.agentbayu.app.domain.tools.ToolApprovalGate
import dev.agentbayu.app.domain.tools.ToolApprovalKind
import dev.agentbayu.app.domain.tools.ToolApprovalRequest
import dev.agentbayu.app.domain.tools.ToolApprovalTicket
import dev.agentbayu.app.platform.files.FileAccess
import dev.agentbayu.app.platform.files.FileAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WriteFileTool(
    private val files: () -> FileAccess,
    private val gate: ToolApprovalGate
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Replace the whole content of a text file, creating the file when it is " +
            "missing. The owner sees the change before it lands.",
        parameters = toolSchema(
            ToolField("path", "string", "File to write"),
            ToolField("content", "string", "The full new content of the file")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val path = arguments.text("path")
            ?: return@withContext call.problem("A path is required")
        val content = arguments.raw("content")
            ?: return@withContext call.problem("A content value is required")
        val access = files()
        val target = try {
            access.resolve(path)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        val existed = target.exists()
        val preview = TextDiff.of(access.textOrEmpty(path), content)
        call.gated(
            gate = gate,
            kind = if (existed) ToolApprovalKind.WRITE else ToolApprovalKind.CREATE,
            path = target.path,
            preview = preview
        ) {
            try {
                access.write(path, content)
                call.reply(summarize(if (existed) "Wrote " else "Created ", target.path, preview))
            } catch (error: FileAccessException) {
                call.problem(error.message.orEmpty())
            }
        }
    }

    private companion object {
        const val NAME = "write_file"
    }
}

class EditFileTool(
    private val files: () -> FileAccess,
    private val gate: ToolApprovalGate
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Change one exact piece of text inside a file. The old_string has to " +
            "appear exactly once, so quote enough surrounding lines to make it unique.",
        parameters = toolSchema(
            ToolField("path", "string", "File to change"),
            ToolField("old_string", "string", "The exact text to look for"),
            ToolField("new_string", "string", "The text that replaces it, empty to remove it")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val path = arguments.text("path")
            ?: return@withContext call.problem("A path is required")
        val old = arguments.text("old_string")
            ?: return@withContext call.problem("An old_string is required")
        val new = arguments.raw("new_string")
            ?: return@withContext call.problem("A new_string is required")
        val access = files()
        val target = try {
            access.resolve(path)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        val before = try {
            access.read(path)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        val hits = occurrences(before, old)
        if (hits == 0) {
            return@withContext call.problem("The old_string is not in " + target.path)
        }
        if (hits > 1) {
            return@withContext call.problem(
                "The old_string appears " + hits + " times in " + target.path +
                    ", so quote more lines around it"
            )
        }
        val after = before.replace(old, new)
        val preview = TextDiff.of(before, after)
        call.gated(gate, ToolApprovalKind.EDIT, target.path, preview = preview) {
            try {
                access.write(path, after)
                call.reply(summarize("Edited ", target.path, preview))
            } catch (error: FileAccessException) {
                call.problem(error.message.orEmpty())
            }
        }
    }

    private companion object {
        const val NAME = "edit_file"
    }
}

class DeleteFileTool(
    private val files: () -> FileAccess,
    private val gate: ToolApprovalGate
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Delete one file, or one folder that is already empty.",
        parameters = toolSchema(
            ToolField("path", "string", "File or empty folder to delete")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val path = ToolArguments(call.arguments).text("path")
            ?: return@withContext call.problem("A path is required")
        val access = files()
        val target = try {
            access.resolve(path)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        if (!target.exists()) return@withContext call.problem("Nothing at " + target.path)
        val preview = TextDiff.of(access.textOrEmpty(path), "")
        call.gated(gate, ToolApprovalKind.DELETE, target.path, preview = preview) {
            try {
                access.delete(path)
                call.reply("Deleted " + target.path)
            } catch (error: FileAccessException) {
                call.problem(error.message.orEmpty())
            }
        }
    }

    private companion object {
        const val NAME = "delete_file"
    }
}

class MoveFileTool(
    private val files: () -> FileAccess,
    private val gate: ToolApprovalGate
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Move or rename a file. The destination must not exist yet.",
        parameters = toolSchema(
            ToolField("from", "string", "File to move"),
            ToolField("to", "string", "Where the file should end up")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val from = arguments.text("from")
            ?: return@withContext call.problem("A from path is required")
        val to = arguments.text("to")
            ?: return@withContext call.problem("A to path is required")
        val access = files()
        val source = try {
            access.resolve(from)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        val destination = try {
            access.resolve(to)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        if (!source.exists()) return@withContext call.problem("Nothing at " + source.path)
        if (destination.exists()) {
            return@withContext call.problem("Already there: " + destination.path)
        }
        call.gated(gate, ToolApprovalKind.MOVE, source.path, destination.path) {
            try {
                access.move(from, to)
                call.reply("Moved " + source.path + " to " + destination.path)
            } catch (error: FileAccessException) {
                call.problem(error.message.orEmpty())
            }
        }
    }

    private companion object {
        const val NAME = "move_file"
    }
}

private suspend fun ToolCall.gated(
    gate: ToolApprovalGate,
    kind: ToolApprovalKind,
    path: String,
    destination: String? = null,
    preview: List<DiffLine> = emptyList(),
    perform: () -> ToolResult
): ToolResult {
    val decision = gate.confirm(
        ToolApprovalRequest(
            id = ToolApprovalTicket.next(),
            toolName = name,
            kind = kind,
            path = path,
            destination = destination,
            preview = preview
        )
    )
    if (decision == ToolApprovalDecision.DENY) {
        return problem("The owner refused " + name + " on " + path)
    }
    return perform()
}

private fun summarize(prefix: String, path: String, preview: List<DiffLine>): String =
    prefix + path + ", +" + TextDiff.added(preview) + " -" + TextDiff.removed(preview)

private fun occurrences(text: String, needle: String): Int {
    var count = 0
    var index = text.indexOf(needle)
    while (index >= 0) {
        count += 1
        index = text.indexOf(needle, index + needle.length)
    }
    return count
}
