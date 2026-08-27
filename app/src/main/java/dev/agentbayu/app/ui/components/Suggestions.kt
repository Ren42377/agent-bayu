package dev.agentbayu.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R

@Composable
fun defaultSuggestions(): List<String> = listOf(
    stringResource(R.string.suggestion_summarize),
    stringResource(R.string.suggestion_tasks),
    stringResource(R.string.suggestion_reminder),
    stringResource(R.string.suggestion_reply)
)
