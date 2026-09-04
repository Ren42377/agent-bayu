package dev.agentbayu.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R

@Composable
internal fun toolDisplayName(name: String): String = when (name) {
    "create_task" -> stringResource(R.string.tool_name_create_task)
    "list_tasks" -> stringResource(R.string.tool_name_list_tasks)
    "complete_task" -> stringResource(R.string.tool_name_complete_task)
    "list_files" -> stringResource(R.string.tool_name_list_files)
    "read_file" -> stringResource(R.string.tool_name_read_file)
    "search_files" -> stringResource(R.string.tool_name_search_files)
    "view_image" -> stringResource(R.string.tool_name_view_image)
    "write_file" -> stringResource(R.string.tool_name_write_file)
    "edit_file" -> stringResource(R.string.tool_name_edit_file)
    "delete_file" -> stringResource(R.string.tool_name_delete_file)
    "move_file" -> stringResource(R.string.tool_name_move_file)
    else -> name
}
