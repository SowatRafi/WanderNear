package com.wandernear.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The one theme wrapper for the whole app. Applies the brand colours, type scale and
 * shapes, follows the system light/dark setting, and keeps the status-bar icons legible
 * against whatever background shows through (we draw edge-to-edge).
 *
 * We deliberately do NOT use Android 12 "dynamic colour" (wallpaper-derived) — WanderNear
 * has its own identity, and it should look the same welcoming teal on every phone.
 */
@Composable
fun WanderNearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // Dark icons on the light theme, light icons on the dark theme.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WnTypography,
        shapes = WnShapes,
        content = content,
    )
}
