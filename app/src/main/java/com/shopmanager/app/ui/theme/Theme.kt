package com.shopmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Maroon = Color(0xFF7A1F3D)
val Gold = Color(0xFFC9962E)
val Success = Color(0xFF2E7D32)
val Warning = Color(0xFFB26A00)
val Danger = Color(0xFFC62828)

private val Light = lightColorScheme(
    primary = Maroon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E1),
    onPrimaryContainer = Color(0xFF3E0018),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEFC9),
    onSecondaryContainer = Color(0xFF3A2A00),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C4),
    onPrimary = Color(0xFF5F112D),
    primaryContainer = Maroon,
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFEBC067),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFEFC9)
)

@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
