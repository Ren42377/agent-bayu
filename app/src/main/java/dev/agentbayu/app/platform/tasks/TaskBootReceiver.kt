package dev.agentbayu.app.platform.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.agentbayu.app.AppGraph

class TaskBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val store = AppGraph.tasks(context)
        AppGraph.taskAlarms(context).sync(store.tasks.value)
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
