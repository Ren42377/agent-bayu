package dev.agentbayu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaultTypography = Typography()

internal val AgentBayuTypography = Typography(
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = defaultTypography.bodyLarge.copy(
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = defaultTypography.bodyMedium.copy(
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = defaultTypography.bodySmall.copy(
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = defaultTypography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    labelMedium = defaultTypography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
)
