package dev.agentbayu.app.platform.files

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

object AllFilesAccess {

    private const val TAG = "AgentBayu"

    const val LEGACY_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

    val needsRuntimePermission: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun granted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(context, LEGACY_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun open(context: Context): Boolean {
        for (intent in intentsFor(context)) {
            try {
                context.startActivity(intent)
                return true
            } catch (error: ActivityNotFoundException) {
                continue
            }
        }
        Log.e(TAG, "No settings screen available for all files access")
        return false
    }

    private fun intentsFor(context: Context): List<Intent> {
        val target = Uri.fromParts("package", context.packageName, null)
        val actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION to target,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION to null,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS to target
            )
        } else {
            listOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS to target)
        }
        return actions.map { (action, data) ->
            Intent(action).apply {
                if (data != null) this.data = data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
