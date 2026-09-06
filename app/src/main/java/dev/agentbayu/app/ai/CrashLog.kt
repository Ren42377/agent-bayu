package dev.agentbayu.app.ai

import android.content.Context

object CrashLog {

    fun record(context: Context, error: Throwable) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TYPE, error.javaClass.simpleName)
            .putString(KEY_DETAIL, error.stackTraceToString().take(LogStore.MAX_CRASH_DETAIL_CHARS))
            .putLong(KEY_AT_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun take(context: Context): CrashEntry? {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val type = preferences.getString(KEY_TYPE, null) ?: return null
        val detail = preferences.getString(KEY_DETAIL, null).orEmpty()
        val atMillis = preferences.getLong(KEY_AT_MILLIS, 0L)
        preferences.edit().clear().apply()
        return CrashEntry(type, detail, atMillis)
    }

    data class CrashEntry(val type: String, val detail: String, val atMillis: Long)

    private const val FILE_NAME = "agent_bayu_last_crash"
    private const val KEY_TYPE = "type"
    private const val KEY_DETAIL = "detail"
    private const val KEY_AT_MILLIS = "at_millis"
}
