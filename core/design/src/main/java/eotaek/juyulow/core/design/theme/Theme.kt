package eotaek.juyulow.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalPleosColors = staticCompositionLocalOf { LightPleosColors }

object JUYULOWTheme {
    val colors: PleosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosColors.current
}

private val LightColorScheme = LightPleosColors.toMaterialColorScheme(darkTheme = false)
private val DarkColorScheme = DarkPleosColors.toMaterialColorScheme(darkTheme = true)

// Pleos does not define Material roles. Keep controls neutral and reserve informative colors
// for their semantic purpose. Explicitly map every role to avoid Material's default palette.
private fun PleosColors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
    val defaults = if (darkTheme) darkColorScheme() else lightColorScheme()
    return defaults.copy(
        primary = buttonFilledEnabled,
        onPrimary = textLightPrimary,
        primaryContainer = surfaceAccent,
        onPrimaryContainer = textDarkPrimary,
        inversePrimary = basic300,
        secondary = basic600,
        onSecondary = textLightPrimary,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = textDarkPrimary,
        tertiary = basic700,
        onTertiary = textLightPrimary,
        tertiaryContainer = surfaceAccent,
        onTertiaryContainer = textDarkPrimary,
        background = backgroundPrimary,
        onBackground = textDarkPrimary,
        surface = surfaceBasic,
        onSurface = textDarkPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textDarkSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = basic800,
        inverseOnSurface = textLightPrimary,
        error = informativeNegative,
        onError = if (darkTheme) basicStaticDark100 else basicStaticLight100,
        errorContainer = surfaceHigh,
        onErrorContainer = informativeNegative,
        outline = basic500,
        outlineVariant = basic200,
        scrim = basicStaticDark100,
        surfaceBright = if (darkTheme) surfaceAccent else surfaceBasic,
        surfaceDim = if (darkTheme) surfaceLow else surfaceHigh,
        surfaceContainerLowest = backgroundSecondary,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surfaceBasic,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceAccent,
        primaryFixed = LightPleosColors.surfaceAccent,
        primaryFixedDim = LightPleosColors.basic300,
        onPrimaryFixed = basicStaticDark100,
        onPrimaryFixedVariant = LightPleosColors.basic600,
        secondaryFixed = LightPleosColors.surfaceHigh,
        secondaryFixedDim = LightPleosColors.basic200,
        onSecondaryFixed = basicStaticDark100,
        onSecondaryFixedVariant = LightPleosColors.basic600,
        tertiaryFixed = LightPleosColors.surfaceAccent,
        tertiaryFixedDim = LightPleosColors.basic300,
        onTertiaryFixed = basicStaticDark100,
        onTertiaryFixedVariant = LightPleosColors.basic700,
    )
}

@Composable
fun JUYULOWTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPleosColors else LightPleosColors
    CompositionLocalProvider(LocalPleosColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
