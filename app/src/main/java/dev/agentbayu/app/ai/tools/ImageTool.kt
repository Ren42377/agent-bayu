package dev.agentbayu.app.ai.tools

import android.util.Base64
import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.platform.ImagePipeline
import dev.agentbayu.app.platform.files.FileAccess
import dev.agentbayu.app.platform.files.FileAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ViewImageTool(
    private val files: () -> FileAccess,
    private val pipeline: () -> ImagePipeline
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Look at a picture stored on the phone. The picture is attached to the " +
            "conversation, so describe what it shows once the call returns.",
        parameters = toolSchema(
            ToolField("path", "string", "Image file to look at")
        ),
        needsVision = true
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val path = ToolArguments(call.arguments).text("path")
            ?: return@withContext call.problem("A path is required")
        val bytes = try {
            files().bytes(path)
        } catch (error: FileAccessException) {
            return@withContext call.problem(error.message.orEmpty())
        }
        val prepared = pipeline().prepareBytes(bytes)
            ?: return@withContext call.problem("That file is not an image: " + path)
        val encoded = Base64.encodeToString(prepared.bytes, Base64.NO_WRAP)
        call.reply(
            content = "Attached " + path + " at " + prepared.width + "x" + prepared.height,
            images = listOf(ChatImage(mimeType = prepared.mimeType, data = encoded))
        )
    }

    private companion object {
        const val NAME = "view_image"
    }
}
