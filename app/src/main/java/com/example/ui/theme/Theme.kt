package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberGold,
    secondary = CyberCyan,
    tertiary = CyberRed,
    background = DarkBg,
    surface = SurfaceBg,
    surfaceVariant = CardBg,
    onBackground = OutdoorPureWhite,
    onSurface = OutdoorPureWhite,
    onPrimary = OutdoorPitchBlack,
    onSecondary = OutdoorPitchBlack,
    outline = CyberCyan
  )

private val LightColorScheme = DarkColorScheme // Force dark theme for professional look

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to dark theme
  dynamicColor: Boolean = false, // Disable dynamic color to enforce branding
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
