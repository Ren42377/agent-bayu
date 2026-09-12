package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.domain.tools.PermissionKind
import dev.agentbayu.app.domain.tools.PermissionRequests

class RequestPermissionTool(private val requests: () -> PermissionRequests) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Ask the owner to grant an Android permission this assistant needs. Call " +
            "this when another tool failed because a permission is missing, instead of telling " +
            "the owner to open the settings themselves. Storage covers reading and writing " +
            "files in the phone storage, notifications covers reminders, and exact_alarms " +
            "covers reminders that must ring on time.",
        parameters = toolSchema(
            ToolField(
                name = "kind",
                type = "string",
                description = "Which permission to ask for: storage, notifications, or exact_alarms"
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val raw = ToolArguments(call.arguments).text("kind")
            ?: return call.problem("A kind is required")
        val kind = PermissionKind.of(raw)
            ?: return call.problem("Unknown permission: " + raw)
        val gate = requests()
        if (gate.isGranted(kind)) return call.reply("Already granted: " + kind.wireValue)
        if (!gate.request(kind)) return call.problem("The owner said no to " + kind.wireValue)
        if (gate.isGranted(kind)) return call.reply("Granted: " + kind.wireValue)
        return call.reply(
            "The Android settings page for " + kind.wireValue + " is open. The owner has to " +
                "turn the switch on there, then the same request can be tried again."
        )
    }

    private companion object {
        const val NAME = "request_permission"
    }
}
