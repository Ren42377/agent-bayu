package dev.agentbayu.app.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class BayuRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent, listener: RecognitionService.Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: RecognitionService.Callback) = Unit

    override fun onCancel(listener: RecognitionService.Callback) = Unit
}
