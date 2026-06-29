package com.example.sharealbum.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MomentoDarkPrimary,
    onPrimary = MomentoDarkBackground,
    secondary = MomentoDarkSecondary,
    onSecondary = MomentoDarkPrimary,
    tertiary = MomentoAccent,
    background = MomentoDarkBackground,
    onBackground = MomentoDarkPrimary,
    surface = MomentoDarkSurface,
    onSurface = MomentoDarkPrimary,
    error = MomentoError
)

private val LightColorScheme = lightColorScheme(
    primary = MomentoPrimary,
    onPrimary = MomentoSurface,
    secondary = MomentoSecondary,
    onSecondary = MomentoPrimary,
    tertiary = MomentoAccent,
    onTertiary = MomentoSurface,
    background = MomentoBackground,
    onBackground = MomentoText,
    surface = MomentoSurface,
    onSurface = MomentoText,
    surfaceVariant = MomentoMuted,
    onSurfaceVariant = MomentoSubtext,
    outline = MomentoBorder,
    error = MomentoError
)

@Composable
fun ShareAlbumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
