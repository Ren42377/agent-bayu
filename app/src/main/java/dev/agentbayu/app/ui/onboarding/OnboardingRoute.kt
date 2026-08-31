package dev.agentbayu.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.assistant.BayuVoiceInteractionService
import dev.agentbayu.app.platform.AssistantRole

@Composable
fun OnboardingRoute(
    onFinish: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val useScreenContext by settings.useScreenContext.collectAsState()
    var isDefaultAssistant by remember { mutableStateOf(AssistantRole.isDefaultAssistant(context)) }
    var isMicrophoneGranted by remember { mutableStateOf(hasMicrophonePermission(context)) }
    val micGrantedMessage = stringResource(R.string.mic_granted_message)
    val micDeniedMessage = stringResource(R.string.mic_denied_message)
    val settingsUnavailable = stringResource(R.string.setup_settings_unavailable)
    val testUnavailable = stringResource(R.string.setup_test_unavailable)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultAssistant = AssistantRole.isDefaultAssistant(context)
                isMicrophoneGranted = hasMicrophonePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicrophoneGranted = granted
        onMessage(if (granted) micGrantedMessage else micDeniedMessage)
    }

    OnboardingScreen(
        isDefaultAssistant = isDefaultAssistant,
        isMicrophoneGranted = isMicrophoneGranted,
        useScreenContext = useScreenContext,
        onOpenAssistantSettings = {
            if (!AssistantRole.openAssistantSettings(context)) {
                onMessage(settingsUnavailable)
            }
        },
        onRequestMicrophone = { microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onScreenContextChange = settings::setUseScreenContext,
        onTestPanel = {
            if (!BayuVoiceInteractionService.showPanel()) {
                onMessage(testUnavailable)
            }
        },
        onFinish = onFinish,
        modifier = modifier
    )
}

private fun hasMicrophonePermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}
