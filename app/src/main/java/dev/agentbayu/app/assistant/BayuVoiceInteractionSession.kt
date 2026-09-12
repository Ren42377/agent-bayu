package dev.agentbayu.app.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSession.AssistState
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R

class BayuVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        setTheme(R.style.Theme_AgentBayu_Session)
        super.onCreate()
    }

    override fun onCreateContentView(): View {
        AppGraph.warmUp(context)
        return View(context)
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        window.window?.let { sessionWindow ->
            WindowCompat.setDecorFitsSystemWindows(sessionWindow, false)
            sessionWindow.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        closeSystemDialogs()
        launchPanel()
    }

    override fun onHide() {
        ScreenContextHolder.clear()
        ScreenShotHolder.clear()
        super.onHide()
    }

    override fun onDestroy() {
        ScreenContextHolder.clear()
        ScreenShotHolder.clear()
        super.onDestroy()
    }

    override fun onHandleAssistState(state: AssistState) {
        state.screenshot?.let { screenshot -> ScreenShotHolder.update(screenshot) }
    }

    @Deprecated("Replaced by onHandleAssist(AssistState) on API 30 and above")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        if (AppGraph.settings(context).useScreenContext.value) {
            ScreenContextHolder.update(structure)
        } else {
            ScreenContextHolder.clear()
        }
    }

    private fun launchPanel() {
        AssistFallbackActivity.onPanelHidden = { hide() }
        val intent = Intent(context, AssistFallbackActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startAssistantActivity(intent)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to start assistant panel activity", error)
            hide()
        }
    }

    private companion object {
        const val TAG = "AgentBayu"
    }
}
