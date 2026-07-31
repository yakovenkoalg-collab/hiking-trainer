package ru.yakovenko.mountainform.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF173B32)
val ForestDark = Color(0xFF0D2923)
val Moss = Color(0xFF5D7D65)
val Sun = Color(0xFFF2C45E)
val Snow = Color(0xFFF7F4ED)
val Stone = Color(0xFF636B67)
val Danger = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8DB),
    onPrimaryContainer = ForestDark,
    secondary = Moss,
    secondaryContainer = Color(0xFFDCE7DD),
    tertiary = Sun,
    background = Snow,
    surface = Color(0xFFFFFCF5),
    surfaceVariant = Color(0xFFE5E7E1),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9D1B6),
    onPrimary = ForestDark,
    primaryContainer = Forest,
    secondary = Color(0xFFB8CCBB),
    tertiary = Sun,
    background = Color(0xFF101714),
    surface = Color(0xFF17201C),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MountainFormTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
