package dev.agentbayu.app.ai.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateAlarmTool(private val context: Context) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Create a real alarm in the phone clock app, so it rings even when this " +
            "assistant is closed. Give the time on a 24 hour clock in the owner local time. " +
            "Leave days empty for an alarm that rings once at the next matching time.",
        parameters = toolSchema(
            ToolField("time", "string", "Alarm time on a 24 hour clock, for example 06:00"),
            ToolField(
                name = "title",
                type = "string",
                description = "Name of the alarm shown in the clock app",
                required = false
            ),
            ToolField(
                name = "days",
                type = "string",
                description = "Days for a repeating alarm as mon, tue, wed, thu, fri, sat, sun, " +
                    "separated by commas",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val arguments = ToolArguments(call.arguments)
        val raw = arguments.text("time")
            ?: return@withContext call.problem("A time is required, for example 06:00")
        val clock = clockOf(raw)
            ?: return@withContext call.problem("Cannot read the time: " + raw)
        val title = arguments.text("title").orEmpty()
        val days = daysOf(arguments.text("days"))
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AlarmClock.EXTRA_HOUR, clock.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, clock.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (title.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, title)
        if (days.isNotEmpty()) {
            intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            return@withContext call.problem(
                "This phone has no clock app that accepts alarms from another app"
            )
        }
        val label = if (title.isEmpty()) "" else " named " + title
        val repeat = if (days.isEmpty()) "" else " repeating on " + arguments.text("days")
        call.reply("Handed a " + format(clock) + " alarm" + label + repeat + " to the clock app")
    }

    private class AlarmTime(val hour: Int, val minute: Int)

    private fun clockOf(raw: String): AlarmTime? {
        val digits = raw.trim().filter { it.isDigit() || it == ':' || it == '.' }
        val parts = digits.split(':', '.')
        if (parts.isEmpty()) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else 0
        if (hour !in 0..MAX_HOUR || minute !in 0..MAX_MINUTE) return null
        return AlarmTime(hour, minute)
    }

    private fun format(clock: AlarmTime): String =
        clock.hour.toString().padStart(2, '0') + ":" + clock.minute.toString().padStart(2, '0')

    private fun daysOf(raw: String?): List<Int> {
        val text = raw?.lowercase() ?: return emptyList()
        return WEEKDAYS.filter { (name, _) -> text.contains(name) }.map { (_, value) -> value }
    }

    private companion object {
        const val NAME = "create_alarm"
        const val MAX_HOUR = 23
        const val MAX_MINUTE = 59
        val WEEKDAYS = listOf(
            "mon" to Calendar.MONDAY,
            "tue" to Calendar.TUESDAY,
            "wed" to Calendar.WEDNESDAY,
            "thu" to Calendar.THURSDAY,
            "fri" to Calendar.FRIDAY,
            "sat" to Calendar.SATURDAY,
            "sun" to Calendar.SUNDAY
        )
    }
}

class DeleteAlarmTool(private val context: Context) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Ask the phone clock app to dismiss an alarm that is currently ringing. " +
            "It cannot delete the saved alarm definition from another app.",
        parameters = toolSchema(
            ToolField(
                name = "alarm_title",
                type = "string",
                description = "Name of the alarm when known",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_ALL)
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            return@withContext call.problem(
                "This phone has no clock app that accepts alarm dismiss requests from another app"
            )
        }
        call.reply("Handed an alarm dismiss request to the clock app")
    }

    private companion object {
        const val NAME = "delete_alarm"
    }
}
