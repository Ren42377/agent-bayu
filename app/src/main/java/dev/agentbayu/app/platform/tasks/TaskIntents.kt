package dev.agentbayu.app.platform.tasks

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.agentbayu.app.MainActivity

internal const val ACTION_TASK_SHOW = "dev.agentbayu.app.action.TASK_SHOW"
internal const val ACTION_TASK_COMPLETE = "dev.agentbayu.app.action.TASK_COMPLETE"
internal const val ACTION_TASK_SNOOZE = "dev.agentbayu.app.action.TASK_SNOOZE"
internal const val EXTRA_TASK_ID = "dev.agentbayu.app.extra.TASK_ID"

private const val REQUEST_MIX = 31

internal fun taskBroadcast(context: Context, taskId: String, action: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        taskRequestCode(taskId, action),
        Intent(context, TaskAlarmReceiver::class.java).also {
            it.action = action
            it.putExtra(EXTRA_TASK_ID, taskId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

internal fun taskContentIntent(context: Context, taskId: String): PendingIntent =
    PendingIntent.getActivity(
        context,
        taskRequestCode(taskId, ACTION_TASK_OPEN),
        Intent(context, MainActivity::class.java).also {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            it.putExtra(EXTRA_TASK_ID, taskId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

internal fun taskNotificationId(taskId: String): Int = taskId.hashCode() and Int.MAX_VALUE

private fun taskRequestCode(taskId: String, action: String): Int =
    (taskId.hashCode() * REQUEST_MIX + action.hashCode()) and Int.MAX_VALUE

private const val ACTION_TASK_OPEN = "dev.agentbayu.app.action.TASK_OPEN"
