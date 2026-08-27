package dev.agentbayu.app.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AssistantPanelController {

    private val visibleState = MutableStateFlow(false)
    private val inputState = MutableStateFlow("")
    private val resetTokenState = MutableStateFlow(0)

    val visible: StateFlow<Boolean> = visibleState.asStateFlow()
    val input: StateFlow<String> = inputState.asStateFlow()
    val resetToken: StateFlow<Int> = resetTokenState.asStateFlow()

    fun show() {
        inputState.value = ""
        visibleState.value = true
    }

    fun requestHide() {
        visibleState.value = false
    }

    fun reset() {
        visibleState.value = false
        inputState.value = ""
        resetTokenState.value = resetTokenState.value + 1
    }

    fun updateInput(value: String) {
        inputState.value = value
    }

    fun takeInput(): String {
        val value = inputState.value.trim()
        if (value.isNotEmpty()) {
            inputState.value = ""
        }
        return value
    }
}
