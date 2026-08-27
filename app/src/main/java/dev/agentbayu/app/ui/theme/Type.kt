package dev.agentbayu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaultTypography = Typography()

internal val AgentBayuTypography = Typography(
    headlineSmall = defaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = defaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaultTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = defaultTypography.bodyLarge.copy(lineHeight = 24.sp),
    labelLarge = defaultTypography.labelLarge.copy(fontWeight = FontWeight.Medium)
)
