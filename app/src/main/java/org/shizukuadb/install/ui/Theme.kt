package org.shizukuadb.install.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AmoledColors = darkColorScheme(
    primary = Color(0xFFB9C7FF),
    onPrimary = Color(0xFF16295F),
    primaryContainer = Color(0xFF314477),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFC2C6DD),
    onSecondary = Color(0xFF2B2F42),
    secondaryContainer = Color(0xFF42465A),
    onSecondaryContainer = Color(0xFFDEE2FA),
    background = Color.Black,
    onBackground = Color(0xFFE5E1E6),
    surface = Color.Black,
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C5D0),
    outline = Color(0xFF8F9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun AmoledTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        AmoledColors
    }
    MaterialTheme(colorScheme = colors.copy(background = Color.Black, surface = Color.Black), content = content)
}
