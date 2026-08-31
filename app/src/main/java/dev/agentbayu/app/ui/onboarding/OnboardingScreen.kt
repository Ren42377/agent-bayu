package dev.agentbayu.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.StatusCard
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets

internal enum class OnboardingStep { ASSISTANT, MICROPHONE, CONTEXT, PANEL }

@Composable
fun OnboardingScreen(
    isDefaultAssistant: Boolean,
    isMicrophoneGranted: Boolean,
    useScreenContext: Boolean,
    onOpenAssistantSettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onScreenContextChange: (Boolean) -> Unit,
    onTestPanel: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    val steps = OnboardingStep.entries
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val isLastStep = stepIndex == steps.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 22.dp,
                bottom = 16.dp + insets.calculateBottomPadding()
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.onboarding_step, stepIndex + 1, steps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                onClick = onFinish,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val direction = if (forward) 1 else -1
                slideInHorizontally(AgentBayuMotion.navSlideSpec) { width ->
                    direction * width / SLIDE_DIVISOR
                } + fadeIn(AgentBayuMotion.navFadeSpec) togetherWith
                    slideOutHorizontally(AgentBayuMotion.navSlideSpec) { width ->
                        -direction * width / SLIDE_DIVISOR
                    } + fadeOut(AgentBayuMotion.navFadeSpec)
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "onboardingStep"
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                StepCard(
                    step = current,
                    isDefaultAssistant = isDefaultAssistant,
                    isMicrophoneGranted = isMicrophoneGranted,
                    useScreenContext = useScreenContext,
                    onOpenAssistantSettings = onOpenAssistantSettings,
                    onRequestMicrophone = onRequestMicrophone,
                    onScreenContextChange = onScreenContextChange,
                    onTestPanel = onTestPanel
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            steps.forEachIndexed { index, _ ->
                val active = index == stepIndex
                Box(
                    modifier = Modifier
                        .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                        .background(
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DOT_ALPHA)
                            },
                            shape = CapsuleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stepIndex > 0) {
                GlassButton(
                    onClick = { stepIndex -= 1 },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_back),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            GlassButton(
                onClick = {
                    if (isLastStep) onFinish() else stepIndex += 1
                },
                modifier = Modifier.weight(1f),
                tint = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isLastStep) R.string.onboarding_finish else R.string.onboarding_next
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    step: OnboardingStep,
    isDefaultAssistant: Boolean,
    isMicrophoneGranted: Boolean,
    useScreenContext: Boolean,
    onOpenAssistantSettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onScreenContextChange: (Boolean) -> Unit,
    onTestPanel: () -> Unit
) {
    when (step) {
        OnboardingStep.ASSISTANT -> StatusCard(
            title = stringResource(R.string.setup_assistant_title),
            body = stringResource(R.string.setup_assistant_body),
            done = isDefaultAssistant,
            hint = stringResource(R.string.setup_assistant_hint),
            actionLabel = stringResource(R.string.setup_assistant_action),
            onAction = onOpenAssistantSettings
        )

        OnboardingStep.MICROPHONE -> StatusCard(
            title = stringResource(R.string.setup_mic_title),
            body = stringResource(R.string.setup_mic_body),
            done = isMicrophoneGranted,
            actionLabel = if (isMicrophoneGranted) {
                null
            } else {
                stringResource(R.string.setup_mic_action)
            },
            onAction = if (isMicrophoneGranted) null else onRequestMicrophone
        )

        OnboardingStep.CONTEXT -> StatusCard(
            title = stringResource(R.string.setup_context_title),
            body = stringResource(R.string.setup_context_body),
            done = useScreenContext,
            checked = useScreenContext,
            onCheckedChange = onScreenContextChange
        )

        OnboardingStep.PANEL -> StatusCard(
            title = stringResource(R.string.setup_test_title),
            body = stringResource(R.string.setup_test_body),
            done = isDefaultAssistant,
            actionLabel = stringResource(R.string.setup_test_action),
            onAction = onTestPanel
        )
    }
}

private const val SLIDE_DIVISOR = 4
private const val DOT_ALPHA = 0.35f
