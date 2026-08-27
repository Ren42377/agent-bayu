package dev.agentbayu.app.platform

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

object AssistantRole {

    private const val TAG = "AgentBayu"
    private const val SECURE_KEY_VOICE_INTERACTION = "voice_interaction_service"
    private const val SECURE_KEY_ASSISTANT = "assistant"

    private val settingsActions = listOf(
        Settings.ACTION_VOICE_INPUT_SETTINGS,
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        Settings.ACTION_SETTINGS
    )

    fun isDefaultAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            }
        }
        return isHeldBySecureSettings(context)
    }

    fun openAssistantSettings(context: Context): Boolean {
        for (action in settingsActions) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (error: ActivityNotFoundException) {
                continue
            }
        }
        Log.e(TAG, "No settings screen available for assistant selection")
        return false
    }

    fun isOwnedByPackage(flattenedComponent: String?, packageName: String): Boolean {
        val value = flattenedComponent?.trim().orEmpty()
        if (value.isEmpty() || packageName.isEmpty()) {
            return false
        }
        val separator = value.indexOf('/')
        val owner = if (separator >= 0) value.substring(0, separator) else value
        return owner == packageName
    }

    private fun isHeldBySecureSettings(context: Context): Boolean {
        val resolver = context.contentResolver
        val packageName = context.packageName
        val keys = listOf(SECURE_KEY_VOICE_INTERACTION, SECURE_KEY_ASSISTANT)
        return keys.any { key ->
            isOwnedByPackage(Settings.Secure.getString(resolver, key), packageName)
        }
    }
}
