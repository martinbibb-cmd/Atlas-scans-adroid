package com.atlasscans.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AtlasPrimary,
    secondary = AtlasSecondary,
    tertiary = AtlasTertiary,
    error = AtlasError,
    surface = AtlasSurface,
    background = AtlasBackground,
    onPrimary = AtlasOnPrimary,
    onSurface = AtlasOnSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = AtlasPrimary,
    secondary = AtlasSecondary,
    tertiary = AtlasTertiary,
    error = AtlasError,
)

@Composable
fun AtlasScansTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
