package xyz.sandboiii.agentcooper.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    secondary = androidx.compose.ui.graphics.Color(0xFF2D2D2D),
    tertiary = androidx.compose.ui.graphics.Color(0xFF3A3A3A)
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
    secondary = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    tertiary = androidx.compose.ui.graphics.Color(0xFFD0D0D0)
)

@Composable
fun AgentCooperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

