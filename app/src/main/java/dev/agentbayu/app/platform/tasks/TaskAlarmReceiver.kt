package dev.agentbayu.app.platform.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.agentbayu.app.AppGraph

class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val store = AppGraph.tasks(context)
        val notifications = TaskNotifications(context)
        when (intent.action) {
            ACTION_TASK_SHOW -> {
                val task = store.find(taskId) ?: return
                if (task.completed) return
                notifications.show(task)
                AppGraph.taskAlarms(context).sync(store.tasks.value)
            }

            ACTION_TASK_COMPLETE -> {
                notifications.cancel(taskId)
                store.setCompleted(taskId, true)
            }

            ACTION_TASK_SNOOZE -> {
                notifications.cancel(taskId)
                AppGraph.taskAlarms(context).snooze(taskId, store.tasks.value)
            }
        }
    }
}
