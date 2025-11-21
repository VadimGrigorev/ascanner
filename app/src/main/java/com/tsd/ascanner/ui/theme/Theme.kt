package com.tsd.ascanner.ui.theme

import android.graphics.ColorSpace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Palette tuned to the provided icon: blue base + fresh green accents (minimal set)
private val BrandPrimary = Color(30, 136, 229)       // primary:rgb(30, 136, 229) (Blue 600)
private val BrandOnPrimary = Color(255, 255, 255)     // onPrimary:rgb(255, 255, 255)
private val BrandSecondary = Color(172, 172, 172)    // secondary:rgb(255, 255, 255) (neutral/clean accent)
private val BrandTertiary = Color(0xFF00BCD4)      // tertiary: #00BCD4 (Cyan 500)

private val LightColors = lightColorScheme(
    // Minimal neutral scheme; real colors come from AppColors via LocalAppColors
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    secondary = BrandSecondary,
    tertiary = BrandTertiary
)

private val AScannerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AScannerTheme(
    useDarkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
 ) {
    // Force light theme only, ignore parameters
    val colorScheme = LightColors
    val appColors = AppPalettes.Light
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            shapes = AScannerShapes,
            content = content
        )
    }
}

