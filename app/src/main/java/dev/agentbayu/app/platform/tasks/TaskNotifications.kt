package dev.agentbayu.app.platform.tasks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem

class TaskNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun show(task: TaskItem) {
        if (!allowed()) return
        ensureChannel()
        val details = task.details.trim()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task)
            .setContentTitle(task.title)
            .setContentText(
                details.ifEmpty { context.getString(R.string.task_notification_body) }
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(taskContentIntent(context, task.id))
            .addAction(
                0,
                context.getString(R.string.task_action_complete),
                taskBroadcast(context, task.id, ACTION_TASK_COMPLETE)
            )
            .addAction(
                0,
                context.getString(R.string.task_action_snooze),
                taskBroadcast(context, task.id, ACTION_TASK_SNOOZE)
            )
        if (details.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(details))
        }
        manager.notify(taskNotificationId(task.id), builder.build())
    }

    fun cancel(taskId: String) = manager.cancel(taskNotificationId(taskId))

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.task_channel_name))
                .setDescription(context.getString(R.string.task_channel_description))
                .build()
        )
    }

    private fun allowed(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CHANNEL_ID = "tasks_reminders"
    }
}
