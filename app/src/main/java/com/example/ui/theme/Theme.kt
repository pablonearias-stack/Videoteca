package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioColorScheme = darkColorScheme(
    primary = CoralEmber,
    onPrimary = StudioObsidian,
    primaryContainer = CoralEmberSoft,
    onPrimaryContainer = CoralEmberLight,
    secondary = CyanSpark,
    onSecondary = StudioObsidian,
    secondaryContainer = CyanSparkSoft,
    onSecondaryContainer = CyanSpark,
    tertiary = AmberFilm,
    onTertiary = StudioObsidian,
    tertiaryContainer = AmberFilmSoft,
    onTertiaryContainer = AmberFilm,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceBright,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = ErrorCrimson,
    onError = StudioObsidian,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorCrimson
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PromptCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
