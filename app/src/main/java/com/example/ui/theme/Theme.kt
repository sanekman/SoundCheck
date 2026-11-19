package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class RtaExtendedColors(
    val spectrumCyan: Color = ElectricCyan,
    val spectrumCyanDim: Color = CyanDim,
    val spectrumCyanGlow: Color = CyanGlow,
    val targetAmber: Color = TargetAmber,
    val targetAmberGlow: Color = AmberGlow,
    val peakCoral: Color = PeakCoral,
    val peakCoralDim: Color = CoralDim,
    val emeraldOk: Color = EmeraldOk,
    val emeraldDim: Color = EmeraldDim,
    val snapshotViolet: Color = SnapshotViolet,
    val snapshotVioletGlow: Color = VioletGlow,
    val gridMajor: Color = GridLineMajor,
    val gridMinor: Color = GridLineMinor,
    val hairline: Color = HairlineBorder,
    val elevatedPanel: Color = ElevatedPanel
)

val LocalRtaColors = staticCompositionLocalOf { RtaExtendedColors() }

private val AutoRtaColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = CyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = TargetAmber,
    onSecondary = ObsidianBg,
    secondaryContainer = ElevatedPanel,
    onSecondaryContainer = TargetAmber,
    tertiary = SnapshotViolet,
    onTertiary = ObsidianBg,
    tertiaryContainer = ElevatedPanel,
    onTertiaryContainer = SnapshotViolet,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlatePanel,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = SlatePanel,
    surfaceContainerHigh = ElevatedPanel,
    surfaceContainerHighest = ElevatedPanel,
    outline = HairlineBorder,
    outlineVariant = GridLineMajor,
    error = PeakCoral,
    onError = ObsidianBg,
    errorContainer = CoralDim,
    onErrorContainer = PeakCoral
)

@Composable
fun AutoRtaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalRtaColors provides RtaExtendedColors()) {
        MaterialTheme(
            colorScheme = AutoRtaColorScheme,
            typography = AutoRtaTypography,
            content = content
        )
    }
}
