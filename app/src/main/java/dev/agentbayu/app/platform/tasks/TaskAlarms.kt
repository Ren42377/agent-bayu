package dev.agentbayu.app.platform.tasks

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.nextTriggerMillis
import dev.agentbayu.app.domain.tasks.triggerAtMillis
import java.time.ZoneId

class TaskAlarms(
    private val context: Context,
    private val clock: Clock = RealClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    private val manager = context.getSystemService(AlarmManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val notifications = TaskNotifications(context)
    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val scheduled = mutableMapOf<String, Long>()
    private val snoozed = HashMap<String, Long>()

    init {
        notifications.ensureChannel()
        loadSnoozed()
    }

    fun sync(tasks: List<TaskItem>) {
        val now = clock.nowMillis()
        val timeZone = zone()
        catchUp(tasks, timeZone, now)
        val wanted = tasks.mapNotNull { task ->
            val snoozedAt = snoozed[task.id]?.takeIf { !task.completed && it > now }
            val at = snoozedAt ?: nextTriggerMillis(task, timeZone, now)
            at?.let { task.id to Reminder(it, snoozedAt != null || task.hasTime) }
        }.toMap()
        (scheduled.keys - wanted.keys).toList().forEach { cancel(it) }
        wanted.forEach { (taskId, reminder) ->
            if (scheduled[taskId] != reminder.atMillis) {
                schedule(taskId, reminder.atMillis, reminder.exactTime)
            }
        }
        scheduled.clear()
        wanted.forEach { (taskId, reminder) -> scheduled[taskId] = reminder.atMillis }
        store.edit().putLong(KEY_WATERMARK, now).apply()
    }

    fun snooze(taskId: String, tasks: List<TaskItem>) {
        snoozed[taskId] = clock.nowMillis() + SNOOZE_MILLIS
        saveSnoozed()
        sync(tasks)
    }

    fun canScheduleExact(): Boolean {
        val alarms = manager ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarms.canScheduleExactAlarms()
    }

    fun isBatteryUnrestricted(): Boolean {
        val service = power ?: return true
        return service.isIgnoringBatteryOptimizations(context.packageName)
    }

    private class Reminder(val atMillis: Long, val exactTime: Boolean)

    private fun catchUp(tasks: List<TaskItem>, timeZone: ZoneId, now: Long) {
        val since = store.getLong(KEY_WATERMARK, 0L)
        if (since <= 0L) return
        val oldest = now - MAX_CATCH_UP_MILLIS
        tasks.asSequence()
            .filter { !it.completed }
            .filter { snoozed[it.id] == null }
            .mapNotNull { task -> triggerAtMillis(task, timeZone)?.let { task to it } }
            .filter { (_, at) -> at > since && at <= now && at >= oldest }
            .sortedBy { (_, at) -> at }
            .take(MAX_CATCH_UP_COUNT)
            .forEach { (task, at) -> notifications.show(task, at) }
    }

    private fun schedule(taskId: String, atMillis: Long, exactTime: Boolean) {
        val alarms = manager ?: return
        val intent = taskBroadcast(context, taskId, ACTION_TASK_SHOW)
        if (!canScheduleExact()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            return
        }
        try {
            if (exactTime) {
                alarms.setAlarmClock(
                    AlarmManager.AlarmClockInfo(atMillis, taskContentIntent(context, taskId)),
                    intent
                )
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Exact alarm rejected for task " + taskId, error)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun cancel(taskId: String) {
        manager?.cancel(taskBroadcast(context, taskId, ACTION_TASK_SHOW))
        if (snoozed.remove(taskId) != null) saveSnoozed()
    }

    private fun loadSnoozed() {
        val raw = store.getString(KEY_SNOOZED, null) ?: return
        val now = clock.nowMillis()
        raw.split(RECORD_SEPARATOR).forEach { record ->
            val parts = record.split(FIELD_SEPARATOR)
            if (parts.size != 2) return@forEach
            val at = parts[1].toLongOrNull() ?: return@forEach
            if (at > now) snoozed[parts[0]] = at
        }
    }

    private fun saveSnoozed() {
        val encoded = snoozed.entries.joinToString(RECORD_SEPARATOR) { (taskId, at) ->
            taskId + FIELD_SEPARATOR + at
        }
        store.edit().putString(KEY_SNOOZED, encoded).apply()
    }

    private companion object {
        const val SNOOZE_MILLIS = 60L * 60L * 1000L
        const val MAX_CATCH_UP_MILLIS = 24L * 60L * 60L * 1000L
        const val MAX_CATCH_UP_COUNT = 5
        const val TAG = "TaskAlarms"
        const val FILE_NAME = "agent_bayu_alarms"
        const val KEY_WATERMARK = "last_sync_millis"
        const val KEY_SNOOZED = "snoozed"
        const val RECORD_SEPARATOR = "|"
        const val FIELD_SEPARATOR = ":"
    }
}
