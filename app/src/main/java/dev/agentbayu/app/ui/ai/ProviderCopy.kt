package dev.agentbayu.app.ui.ai

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.RiskLevel

@Composable
fun authKindLabel(authKind: AuthKind): String = stringResource(
    when (authKind) {
        AuthKind.NONE -> R.string.auth_kind_none
        AuthKind.API_KEY -> R.string.auth_kind_api_key
        AuthKind.OAUTH_DEVICE -> R.string.auth_kind_oauth_device
        AuthKind.OAUTH_PKCE -> R.string.auth_kind_oauth_pkce
    }
)

@Composable
fun authKindSectionLabel(authKind: AuthKind): String = stringResource(
    when (authKind) {
        AuthKind.NONE -> R.string.auth_section_none
        AuthKind.API_KEY -> R.string.auth_section_api_key
        AuthKind.OAUTH_DEVICE -> R.string.auth_section_oauth_device
        AuthKind.OAUTH_PKCE -> R.string.auth_section_oauth_pkce
    }
)

@StringRes
fun riskNotice(risk: RiskLevel): Int? = when (risk) {
    RiskLevel.NONE -> null
    RiskLevel.TOS_GRAY -> R.string.risk_tos_gray
    RiskLevel.FRAGILE -> R.string.risk_fragile
}

@StringRes
fun providerHint(providerId: String): Int? = when (providerId) {
    PROVIDER_OPENCODE -> R.string.provider_hint_opencode
    PROVIDER_OPENAI_COMPATIBLE -> R.string.provider_hint_openai_compatible
    else -> null
}

private const val PROVIDER_OPENCODE = "opencode"
private const val PROVIDER_OPENAI_COMPATIBLE = "openai-compatible"
