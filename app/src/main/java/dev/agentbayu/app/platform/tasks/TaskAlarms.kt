package dev.agentbayu.app.platform.tasks

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.nextTriggerMillis
import java.time.ZoneId

class TaskAlarms(
    private val context: Context,
    private val clock: Clock = RealClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    private val manager = context.getSystemService(AlarmManager::class.java)
    private val scheduled = mutableMapOf<String, Long>()
    private val snoozed = mutableMapOf<String, Long>()

    fun sync(tasks: List<TaskItem>) {
        val now = clock.nowMillis()
        val timeZone = zone()
        val wanted = tasks.mapNotNull { task ->
            val at = snoozed[task.id]?.takeIf { !task.completed && it > now }
                ?: nextTriggerMillis(task, timeZone, now)
            at?.let { task.id to it }
        }.toMap()
        (scheduled.keys - wanted.keys).toList().forEach { cancel(it) }
        wanted.forEach { (taskId, at) -> if (scheduled[taskId] != at) schedule(taskId, at) }
        scheduled.clear()
        scheduled.putAll(wanted)
    }

    fun snooze(taskId: String, tasks: List<TaskItem>) {
        snoozed[taskId] = clock.nowMillis() + SNOOZE_MILLIS
        sync(tasks)
    }

    fun canScheduleExact(): Boolean {
        val alarms = manager ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarms.canScheduleExactAlarms()
    }

    private fun schedule(taskId: String, atMillis: Long) {
        val alarms = manager ?: return
        val intent = taskBroadcast(context, taskId, ACTION_TASK_SHOW)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun cancel(taskId: String) {
        manager?.cancel(taskBroadcast(context, taskId, ACTION_TASK_SHOW))
        snoozed.remove(taskId)
    }

    private companion object {
        const val SNOOZE_MILLIS = 60L * 60L * 1000L
    }
}
