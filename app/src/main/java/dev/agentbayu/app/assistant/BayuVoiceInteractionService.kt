package dev.agentbayu.app.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

class BayuVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        active = this
    }

    override fun onShutdown() {
        clearActive()
        super.onShutdown()
    }

    override fun onDestroy() {
        clearActive()
        super.onDestroy()
    }

    private fun clearActive() {
        if (active === this) {
            active = null
        }
    }

    companion object {

        private const val TAG = "AgentBayu"

        @Volatile
        private var active: BayuVoiceInteractionService? = null

        fun isActive(): Boolean = active != null

        fun showPanel(): Boolean {
            val service = active ?: return false
            return try {
                service.showSession(Bundle(), 0)
                true
            } catch (error: RuntimeException) {
                Log.e(TAG, "Unable to show assistant session", error)
                false
            }
        }
    }
}
