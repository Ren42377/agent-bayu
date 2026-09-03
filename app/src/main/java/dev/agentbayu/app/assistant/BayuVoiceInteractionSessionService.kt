package dev.agentbayu.app.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dev.agentbayu.app.AppGraph

class BayuVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.warmUp(this)
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return BayuVoiceInteractionSession(this)
    }
}
